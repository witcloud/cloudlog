#!/usr/bin/env bash
# ============================================================
# CloudLog 容器化部署的最小闭环验证（business-model.md §3.3「自托管部署」）
#
# 走通：注册 → 提管理员 → 发文 → 详情页可读 → robots.txt / sitemap.xml / feed.xml 在域名根可达，
# 结束后自行清理容器与数据卷。用法：
#
#   ./deploy/verify-deploy.sh          # 跑完即清理
#   ./deploy/verify-deploy.sh --keep   # 跑完保留容器，便于用浏览器手工看页面
#
# 前置：本机有 docker（含 compose 子命令），仓库根已有填好的 .env。
# 它假定 mysql-data 卷是空的（首次起跑会导入 docs/schema.sql）；复用旧卷时库里有存量文章，
# 断言只看这一次新发的文章 id，不受影响，但注册计数与频控额度会被历史消耗过。
# ============================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

KEEP=0
if [[ "${1:-}" == "--keep" ]]; then
    KEEP=1
fi

RUN_TAG="$(date +%s)"
TEST_EMAIL="deploy-verify-$RUN_TAG@example.com"
# 用户名也必须带 RUN_TAG：--keep 保留数据卷，写死的用户名会让第二次跑撞「该用户名已被占用」
TEST_USERNAME="deploybot-$RUN_TAG"
TEST_PASSWORD="DeployVerify-2026"
CATEGORY_NAME="容器化部署验证-$RUN_TAG"
POST_TITLE="容器化部署最小闭环验证 $RUN_TAG"
BASE_URL="http://127.0.0.1:80"

say() { printf '\n\033[1m› %s\033[0m\n' "$*"; }

die() {
    printf '\n\033[31m✗ %s\033[0m\n' "$*" >&2
    echo '--- docker compose ps ---' >&2
    docker compose ps >&2 || true
    echo '--- api 最近 40 行日志 ---' >&2
    docker compose logs --tail 40 api >&2 || true
    exit 1
}

# JSON 取值：优先 python3，退回 jq；两个都没有就不拿正则猜边界
json_at() {
    local body="$1" path="$2"
    if command -v python3 >/dev/null 2>&1; then
        printf '%s' "$body" | python3 -c '
import json, sys
data = json.loads(sys.stdin.read() or "null")
for part in sys.argv[1].split("."):
    if part:
        data = data[int(part)] if isinstance(data, list) else (data or {}).get(part)
print("" if data is None else data)
' "$path" 2>/dev/null || true
    elif command -v jq >/dev/null 2>&1; then
        printf '%s' "$body" | jq -r ".$path" 2>/dev/null || true
    else
        die "需要 python3 或 jq 才能解析接口响应"
    fi
}

assert_contains() {
    local haystack="$1" needle="$2" label="$3"
    if [[ "$haystack" != *"$needle"* ]]; then
        # 变量后贴全角标点必须 ${...}：macOS bash 3.2 会把多字节字符并进变量名（set -u 即崩）
        die "${label}：输出里没有「${needle}」。实际开头：$(printf '%.160s' "$haystack")"
    fi
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        die "这台机器上没有 docker 命令，容器验证跑不了"
    fi
    if ! docker compose version >/dev/null 2>&1; then
        die "docker 没有 compose 子命令"
    fi
    if [[ ! -f .env ]]; then
        die "仓库根缺 .env：先 cp .env.example .env 并填好凭据"
    fi
}

load_env() {
    set -a
    # shellcheck disable=SC1091
    source ./.env
    set +a
    BASE_URL="http://127.0.0.1:${WEB_PORT:-80}"
    API_URL="$BASE_URL/api/v1"
    if [[ -z "${MYSQL_ROOT_PASSWORD:-}" || -z "${MYSQL_DATABASE:-}" || -z "${REDIS_PASSWORD:-}" ]]; then
        die ".env 里 MYSQL_ROOT_PASSWORD / MYSQL_DATABASE / REDIS_PASSWORD 不能空着"
    fi
}

bring_up() {
    say "构建并起跑服务（mysql / redis / api / web）"
    if ! docker compose up -d --build; then
        die "compose 起跑失败"
    fi

    say "等 web 容器健康（api 变 healthy 是 web 起跑的前提）"
    local id status
    id="$(docker compose ps -q web)"
    status=starting
    for _ in $(seq 1 60); do
        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")"
        if [[ "$status" == "healthy" ]]; then
            return 0
        fi
        sleep 2
    done
    die "web 容器 120 秒内没有变成 healthy（当前 ${status}）"
}

send_code_and_read() {
    say "取注册验证码：POST /auth/code（SMTP 是占位值时这一步会报错，但码已经先写进 Redis）"
    local body code=""
    body="$(curl -s -X POST "$API_URL/auth/code" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$TEST_EMAIL\"}" || true)"
    if [[ "$(json_at "$body" code)" != "200" ]]; then
        echo "  发码接口返回：$(printf '%.200s' "$body")"
        echo "  ↑ 没配真实 SMTP 时属预期：VerificationCodeService 先写 Redis 再投递，投递失败不影响取码"
    fi

    for _ in $(seq 1 5); do
        # 取回的值还要去掉双引号：RedisTemplate 的 value 用 JSON 序列化器写 String，
        # 落库形态是 "380519"（带引号），原样拼进请求体就成了 {"code":""380519""}，
        # 后端返「请求体格式错误」（实测 Jackson 报 was expecting comma to separate Object entries）
        code="$(docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning \
            GET "auth:code:$TEST_EMAIL" | tr -d '\r"' || true)"
        if [[ -n "$code" ]]; then
            break
        fi
        sleep 1
    done
    if [[ -z "$code" ]]; then
        die "Redis 里没有 auth:code:${TEST_EMAIL}，注册链路走不通（检查 SMTP_* 是否留空导致后端起不来）"
    fi
    echo "  验证码已从 Redis 取到"
    VERIFY_CODE="$code"
}

register_and_promote() {
    say "注册 $TEST_EMAIL"
    local body
    body="$(curl -s -X POST "$API_URL/auth/register" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$TEST_EMAIL\",\"username\":\"$TEST_USERNAME\",\"password\":\"$TEST_PASSWORD\",\"code\":\"$VERIFY_CODE\"}")"
    if [[ "$(json_at "$body" code)" != "200" ]]; then
        die "注册失败：$body"
    fi

    say "把该账号提成博主（角色只能手工提升，后台没有入口——README 第 4 步）"
    if ! docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
        -e "UPDATE users SET role = 'admin' WHERE email = '$TEST_EMAIL'" >/dev/null; then
        die "提升角色失败"
    fi
}

login() {
    say "登录取 token"
    local body
    body="$(curl -s -X POST "$API_URL/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")"
    TOKEN="$(json_at "$body" data.token)"
    if [[ -z "$TOKEN" ]]; then
        die "登录失败：$body"
    fi
}

publish_post() {
    say "建分类并发一篇已发布文章"
    curl -s -X POST "$API_URL/admin/categories" -H 'Content-Type: application/json' \
        -H "Authorization: Bearer $TOKEN" \
        -d "{\"name\":\"$CATEGORY_NAME\",\"description\":\"deploy/verify-deploy.sh 建的验证分类\"}" >/dev/null

    local list
    list="$(curl -s "$API_URL/categories")"
    CATEGORY_ID="$(printf '%s' "$list" | python3 -c '
import json, sys
for item in json.load(sys.stdin)["data"]:
    if item["name"] == sys.argv[1]:
        print(item["id"])
        break
' "$CATEGORY_NAME" 2>/dev/null || true)"
    if [[ -z "$CATEGORY_ID" ]]; then
        die "没找到刚建的分类：$(printf '%.200s' "$list")"
    fi

    local body
    body="$(curl -s -X POST "$API_URL/articles" -H 'Content-Type: application/json' \
        -H "Authorization: Bearer $TOKEN" \
        -d "{\"title\":\"$POST_TITLE\",\"content\":\"容器化部署验证正文。\",\"categoryId\":$CATEGORY_ID,\"status\":\"published\"}")"
    POST_ID="$(json_at "$body" data.id)"
    if [[ -z "$POST_ID" ]]; then
        die "发文失败：$body"
    fi
    echo "  文章 id=${POST_ID}，分类 id=${CATEGORY_ID}"
}

check_readable() {
    say "读者侧可读：不带 token 的详情接口 + SPA 深链接"
    local detail page_code shell
    detail="$(curl -s "$API_URL/articles/$POST_ID")"
    if [[ "$(json_at "$detail" code)" != "200" ]]; then
        die "详情接口没返回 200：$detail"
    fi
    assert_contains "$detail" "$POST_TITLE" "详情接口响应"

    page_code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/articles/$POST_ID")"
    if [[ "$page_code" != "200" ]]; then
        die "SPA 深链接 $BASE_URL/articles/$POST_ID 返回 $page_code"
    fi
    shell="$(curl -s "$BASE_URL/articles/$POST_ID")"
    assert_contains "$shell" '<div id="app">' "SPA 深链接响应"
}

check_seo() {
    say "三份 SEO 产物在域名根可达（靠 location = 精确匹配，不是 try_files 回落的 HTML）"
    local robots sitemap feed
    robots="$(curl -s "$BASE_URL/robots.txt")"
    assert_contains "$robots" 'User-agent: *' "域名根 /robots.txt"
    assert_contains "$robots" "/sitemap.xml" "域名根 /robots.txt 的 Sitemap 行"
    if [[ "$robots" == *"<!doctype html"* ]]; then
        die "/robots.txt 拿到的是 HTML，说明三条精确映射没生效"
    fi

    sitemap="$(curl -s "$BASE_URL/sitemap.xml")"
    assert_contains "$sitemap" '<urlset' "域名根 /sitemap.xml"
    assert_contains "$sitemap" "/articles/$POST_ID" "/sitemap.xml 里的新文章"

    feed="$(curl -s "$BASE_URL/feed.xml")"
    assert_contains "$feed" '<rss version="2.0">' "域名根 /feed.xml"
    assert_contains "$feed" "$POST_TITLE" "/feed.xml 里的新文章"

    echo "  三份都从域名根直出，且都不是 index.html 回落"
}

clean_up() {
    if [[ "$KEEP" == "1" ]]; then
        say "--keep：容器与数据卷保留，站点在 $BASE_URL"
        return 0
    fi
    say "清理容器与数据卷"
    if ! docker compose down -v --remove-orphans >/dev/null; then
        die "清理失败"
    fi
    local left
    left="$(docker volume ls --format '{{.Name}}' | grep -E '(^|_)cloudlog' || true)"
    if [[ -n "$left" ]]; then
        die "还有残留数据卷：$left"
    fi
    echo "  容器与卷已清空（镜像留在本机，需要时 docker compose down --rmi local）"
    return 0
}

main() {
    require_docker
    load_env
    bring_up
    send_code_and_read
    register_and_promote
    login
    publish_post
    check_readable
    check_seo
    clean_up
    printf '\n\033[32m✓ 最小闭环跑通：注册 → 发文 → 详情可读 → 三份 SEO 产物在域名根可达 → 已清理\033[0m\n'
}

main
