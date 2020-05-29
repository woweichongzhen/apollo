package com.ctrip.framework.apollo.common.controller;

import com.ctrip.framework.apollo.common.exception.AbstractApolloHttpException;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpStatusCodeException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;
import static org.springframework.http.HttpStatus.*;

/**
 * 全局异常处理
 */
@ControllerAdvice
public class GlobalDefaultExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDefaultExceptionHandler.class);

    private static Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();

    private Gson gson = new Gson();

    /**
     * 处理系统内置的Exception，返回500状态码
     *
     * @param request 请求
     * @param ex      异常
     * @return 返回码
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> exception(HttpServletRequest request, Throwable ex) {
        return handleError(request, INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeException.class})
    public ResponseEntity<Map<String, Object>> badRequest(HttpServletRequest request,
                                                          ServletException ex) {
        return handleError(request, BAD_REQUEST, ex, WARN);
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<Map<String, Object>> restTemplateException(HttpServletRequest request,
                                                                     HttpStatusCodeException ex) {
        return handleError(request, ex.getStatusCode(), ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDeny(HttpServletRequest request,
                                                          AccessDeniedException ex) {
        return handleError(request, FORBIDDEN, ex);
    }

    /**
     * 处理自定义Exception
     *
     * @param request http请求
     * @param ex      异常
     * @return 返回体
     */
    @ExceptionHandler({AbstractApolloHttpException.class})
    public ResponseEntity<Map<String, Object>> badRequest(HttpServletRequest request, AbstractApolloHttpException ex) {
        return handleError(request, ex.getHttpStatus(), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(
            HttpServletRequest request,
            MethodArgumentNotValidException ex) {
        final Optional<ObjectError> firstError = ex.getBindingResult().getAllErrors().stream().findFirst();
        if (firstError.isPresent()) {
            final String firstErrorMessage = firstError.get().getDefaultMessage();
            return handleError(request, BAD_REQUEST, new BadRequestException(firstErrorMessage));
        }
        return handleError(request, BAD_REQUEST, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(
            HttpServletRequest request, ConstraintViolationException ex
    ) {
        return handleError(request, BAD_REQUEST, new BadRequestException(ex.getMessage()));
    }

    /**
     * 真正处理异常，构建返回体，日志打印等级为ERROR
     *
     * @param request http请求
     * @param status  http状态码
     * @param ex      异常
     * @return http返回体
     */
    private ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request,
                                                            HttpStatus status, Throwable ex) {
        return handleError(request, status, ex, ERROR);
    }

    /**
     * 构建返回体，打印不同日志等级
     *
     * @param request  http请求
     * @param status   http状态码
     * @param ex       异常
     * @param logLevel 日志等级
     * @return http返回体
     */
    private ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request,
                                                            HttpStatus status, Throwable ex, Level logLevel) {
        String message = ex.getMessage();
        // 打印日志
        printLog(message, ex, logLevel);

        // 错误属性构建，status，message，timestamp，exception
        Map<String, Object> errorAttributes = new HashMap<>();
        boolean errorHandled = false;

        if (ex instanceof HttpStatusCodeException) {
            try {
                //try to extract the original error info if it is thrown from apollo programs, e.g. admin service
                // 如果原始错误信息是从阿波罗程序抛出的，则尝试提取该错误信息，例如管理服务
                errorAttributes = gson.fromJson(((HttpStatusCodeException) ex).getResponseBodyAsString(), mapType);
                status = ((HttpStatusCodeException) ex).getStatusCode();
                errorHandled = true;
            } catch (Throwable th) {
                //ignore
            }
        }

        // 处理apollo抛出异常
        if (!errorHandled) {
            errorAttributes.put("status", status.value());
            errorAttributes.put("message", message);
            errorAttributes.put("timestamp",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            errorAttributes.put("exception", ex.getClass().getName());
        }

        // 返回头添加
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        // 返回体构建
        return new ResponseEntity<>(errorAttributes, headers, status);
    }

    /**
     * 打印日志, 其中logLevel为日志级别: ERROR/WARN/DEBUG/INFO/TRACE
     *
     * @param message  打印内容
     * @param ex       异常
     * @param logLevel 日志等级
     */
    private void printLog(String message, Throwable ex, Level logLevel) {
        switch (logLevel) {
            case ERROR:
                logger.error(message, ex);
                break;
            case WARN:
                logger.warn(message, ex);
                break;
            case DEBUG:
                logger.debug(message, ex);
                break;
            case INFO:
                logger.info(message, ex);
                break;
            case TRACE:
                logger.trace(message, ex);
                break;
            default:
                break;
        }

        Tracer.logError(ex);
    }

}
