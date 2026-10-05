#!/usr/bin/env bash
# ============================================================
# CloudLog 数据库逻辑备份（容器化部署用；business-model.md §3.3「自托管部署」）
#
#   ./deploy/backup-db.sh                  # 备份到 ./backups，保留 14 天
#   KEEP_DAYS=30 ./deploy/backup-db.sh     # 改保留天数
#   BACKUP_DIR=/var/backups/cloudlog ./deploy/backup-db.sh   # 改落盘位置
#
# 挂 crontab（每天 02:30，避开 03:30 的日志清理任务）：
#   30 2 * * * cd /path/to/cloudlog && ./deploy/backup-db.sh >> /var/log/cloudlog-backup.log 2>&1
#
# 为什么走 docker compose exec 而不是宿主的 mysqldump：容器化部署时宿主机上不一定装了 mysql 客户端，
# 而镜像里一定有，凭据也只在仓库根的 .env 里。
#
# 这个脚本刻意不「跑完就算成功」：mysqldump 被截断（磁盘满、连接中断、Ctrl-C）产出的文件和完整备份
# 长得几乎一样，只有尾部的完成标记能区分。所以下面三样都验——gzip 完整性、完成标记、库里的表数与
# dump 里的建表语句数一致；任何一样不过就删掉这份文件并非零退出，绝不留一个看着像备份的空壳。
# 落盘也先写临时名再 mv，避免半途失败留下半个文件。
# ============================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"

say() { printf '\033[1m› %s\033[0m\n' "$*"; }
die() {
    printf '\033[31m✗ %s\033[0m\n' "$*" >&2
    exit 1
}

command -v docker >/dev/null 2>&1 || die "这台机器上没有 docker 命令"
docker compose version >/dev/null 2>&1 || die "docker 没有 compose 子命令"
[[ -f .env ]] || die "仓库根缺 .env：先 cp .env.example .env 并填好凭据"

set -a
# shellcheck disable=SC1091
source ./.env
set +a
[[ -n "${MYSQL_ROOT_PASSWORD:-}" && -n "${MYSQL_DATABASE:-}" ]] || die ".env 里 MYSQL_ROOT_PASSWORD / MYSQL_DATABASE 不能空着"

docker compose ps --status running --services 2>/dev/null | grep -qx mysql \
    || die "mysql 容器没在跑，先 docker compose up -d"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%F_%H%M%S)"
FINAL="$BACKUP_DIR/cloudlog-$STAMP.sql.gz"
TMP="$FINAL.part"
trap 'rm -f "$TMP"' EXIT

say "从 mysql 容器导出 $MYSQL_DATABASE → $FINAL"
# --single-transaction：InnoDB 一致性快照，不锁表，备份期间站点照常写
# --routines：存储过程/触发器一并带走（schema.sql 里没有，但库里若有就不该丢）
# --default-character-set=utf8mb4：正文是中文，恢复时不靠客户端默认值
docker compose exec -T mysql mysqldump \
    -uroot -p"$MYSQL_ROOT_PASSWORD" \
    --single-transaction --routines --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE" 2> >(grep -v 'Using a password' >&2) | gzip -6 > "$TMP"

# gzip 管道里任何一环失败都会让上面的退出码非零（pipefail），这里再逐项验内容
gzip -t "$TMP" || die "gzip 完整性检查失败：$TMP 不是有效的压缩文件"

if ! gzip -dc "$TMP" | tail -5 | grep -q '^-- Dump completed'; then
    die "dump 尾部没有「-- Dump completed」标记，说明导出被截断（磁盘满 / 连接断 / 中途中断）"
fi

LIVE_TABLES="$(docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MYSQL_DATABASE' AND table_type='BASE TABLE'" \
    2>/dev/null | tr -d '[:space:]')"
# 用 gzip -dc 而不是 zcat：BSD（macOS）的 zcat 会给文件名补 .Z 再找，同一个脚本在 Linux 上却正常
DUMP_TABLES="$(gzip -dc "$TMP" | grep -c '^CREATE TABLE')"
[[ "$LIVE_TABLES" == "$DUMP_TABLES" ]] \
    || die "表数对不上：库里有 $LIVE_TABLES 张，dump 里只有 $DUMP_TABLES 张建表语句"

mv "$TMP" "$FINAL"
trap - EXIT

SIZE="$(du -h "$FINAL" | cut -f1 | tr -d '[:space:]')"
# ${...} 不是风格洁癖：macOS 自带 bash 3.2 会把紧跟变量的多字节字符并进变量名，
# set -u 下直接 unbound 崩掉（实测 $FINAL（ 就是崩点），凡变量后贴全角标点一律加花括号
say "完成：${FINAL}（${SIZE}，${DUMP_TABLES} 张表，完成标记与 gzip 完整性均已校验）"

# 滚动清理：只删本脚本自己的命名前缀，别顺手删掉同目录里别人的东西
REMOVED="$(find "$BACKUP_DIR" -maxdepth 1 -name 'cloudlog-*.sql.gz' -mtime "+$KEEP_DAYS" -print -delete | wc -l | tr -d '[:space:]')"
say "保留 $KEEP_DAYS 天，本次清理 $REMOVED 份过期备份"

say "恢复：gunzip < $FINAL | docker compose exec -T mysql mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $MYSQL_DATABASE"
