package xyz.gg_jskang.moviesites.system.web;
import java.util.Map;
import java.util.concurrent.*;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.support.ExpiredQueryException;
@RestControllerAdvice public final class ApiExceptionHandler {
        private final UiLocale locale;
        public ApiExceptionHandler(){this(new UiLocale("en"));}
        @org.springframework.beans.factory.annotation.Autowired
        public ApiExceptionHandler(UiLocale locale){this.locale=locale;}
        @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,String>> error(Exception exception){
            Throwable e=exception;while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();
            int status;String code,message;
            if(e instanceof IllegalArgumentException){status=400;code="INVALID_PATH";message=locale.text("Check the requested path or configuration.","요청 경로 또는 설정을 확인해 주세요.");}
            else if(e instanceof ExpiredQueryException){status=410;code="CURSOR_EXPIRED";message=locale.text("This list has expired. Refresh to continue.","목록 조회 시간이 만료되었습니다. 새로고침해 주세요.");}
            else if(e instanceof NoSuchFileException){status=404;code="NOT_FOUND";message=locale.text("This item was moved or deleted. Refresh the list.","항목이 이동했거나 삭제되었습니다. 목록을 새로고침해 주세요.");}
            else if(e instanceof AccessDeniedException){status=403;code="ACCESS_DENIED";message=locale.text("You do not have permission to access this item.","이 항목에 접근할 권한이 없습니다.");}
            else if(e instanceof RejectedExecutionException){status=429;code="BUSY";message=locale.text("Too many requests. Try again in a moment.","처리 중인 요청이 많습니다. 잠시 후 다시 시도해 주세요.");}
            else if(e instanceof IllegalStateException){status=409;code="NOT_READY";message=locale.text("This video has changed or is being prepared. Check again in a moment.","영상이 변경되었거나 재생 준비 중입니다. 다시 확인해 주세요.");}
            else if(e instanceof IOException||e instanceof TimeoutException){status=503;code="STORAGE_UNAVAILABLE";message=locale.text("MediaStore is unavailable or is taking too long to respond.","저장소에 연결할 수 없거나 응답이 지연되고 있습니다.");}
            else if(e instanceof org.springframework.web.ErrorResponse response){status=response.getStatusCode().value();code="HTTP_ERROR";message=status==404?locale.text("The requested item was not found.","요청한 항목을 찾을 수 없습니다."):locale.text("Check the request format.","요청 형식을 확인해 주세요.");}
            else{status=500;code="INTERNAL_ERROR";message=locale.text("Cannot process this request. Try again in a moment.","요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");}
            return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code",code,"message",message));
        }
    }
