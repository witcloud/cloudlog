package com.ayun.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器：把业务异常统一转为 Result 结构，前端拦截器可读取 message 提示
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 数据库层异常。message 由 MyBatis 拼出，含表名、SQL 片段与 mapper 文件的绝对路径，
     * 属于服务端内部结构，只能进日志；比下面的 RuntimeException 兜底更具体，故优先命中
     * @param e 数据访问异常
     * @return 通用不可用提示
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDataAccess(DataAccessException e) {
        log.error("数据库访问失败", e);
        return Result.error(500, "服务暂时不可用，请稍后重试");
    }

    /**
     * 唯一键冲突。注册与改邮箱都是「先查重再插入」，并发下两条请求可以同时通过查重，
     * 此时由数据库的唯一索引兜底：这是调用方可读的拒绝（换个邮箱就行），不该冒 500，
     * 且比上面的 DataAccessException 兜底更具体，故优先命中
     * @param e 唯一键冲突异常
     * @return 占用提示
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        // 只记事件不记冲突值：Duplicate entry 里带着用户邮箱，与下面 handleTypeMismatch 不记原始入参同口径
        log.warn("唯一键冲突，插入被数据库拒绝");
        return Result.error(400, "邮箱或用户名已被占用");
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(401, "邮箱或密码错误");
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleDisabled(DisabledException e) {
        return Result.error(401, e.getMessage() != null ? e.getMessage() : "账号已被禁用");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        return Result.error(401, "登录状态无效，请重新登录");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "无权限执行此操作");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数不合法";
        return Result.error(400, message);
    }

    /**
     * 查询参数绑到 DTO 后校验不通过（GET 列表接口的 page / pageSize）。@RequestBody 那条路径抛的是
     * MethodArgumentNotValidException、由上面处理；GET 绑定的 model attribute 抛的是它的父类 BindException，
     * 不接就落到 MVC 的默认错误页，前端读不到「每页条数不能超过 50」这句提示
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        FieldError fieldError = e.getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数不合法";
        return Result.error(400, message);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NotFoundException e) {
        return Result.error(404, e.getMessage() != null ? e.getMessage() : "资源不存在");
    }

    /**
     * 路径/查询参数类型不匹配。message 含 Java 类型名与原始入参，同样是服务端内部结构；
     * 日志只记参数名，不记原始值
     * @param e 类型不匹配异常
     * @return 通用参数提示
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不合法: name={}", e.getName());
        return Result.error(400, "请求参数不合法");
    }

    /**
     * 业务异常：参数校验不通过、状态冲突、资源不存在。提示语是给调用方看的，原样带回
     * @param e 业务异常
     * @return 400 + 原提示语
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBiz(BizException e) {
        return Result.error(400, e.getMessage());
    }

    /**
     * 上传体积超过 multipart 上限。注释见 UploadController：调用方可读的拒绝（换小一点的文件），
     * 不接这条就会落到 RuntimeException 兜底变 500
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(400, "上传文件大小超出限制");
    }

    /**
     * 请求体不是合法 JSON 或与目标类型不符。同样是调用方可读的输入错误，报 400 而非 500；
     * message 含反序列化细节，只进日志
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.error(400, "请求体格式错误");
    }

    /**
     * 未归类的运行时异常兜底：包装的是 IO、网络或第三方调用的失败，message 属于服务端内部结构，
     * 只能进日志；对外一律 500 通用文案。业务拒绝请抛 BizException，不要靠这条兜底传话
     * @param e 运行时异常
     * @return 通用不可用提示
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntime(RuntimeException e) {
        log.error("服务运行异常", e);
        return Result.error(500, "服务暂时不可用，请稍后重试");
    }
}
