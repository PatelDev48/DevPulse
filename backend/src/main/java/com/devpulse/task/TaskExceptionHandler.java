package com.devpulse.task;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(com.devpulse.project.ProjectService.ArchivedProjectException.class)
	public ProblemDetail handleArchivedProject(com.devpulse.project.ProjectService.ArchivedProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This project is archived. Restore it before making changes.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Team membership is required for this task action.");
	}

	@ExceptionHandler(TaskService.MissingProjectException.class)
	public ProblemDetail handleMissingProject(TaskService.MissingProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project not found in this team.");
	}

	@ExceptionHandler(TaskService.MissingTaskException.class)
	public ProblemDetail handleMissingTask(TaskService.MissingTaskException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Task not found in this project.");
	}

	@ExceptionHandler(TaskService.InvalidAssigneeException.class)
	public ProblemDetail handleInvalidAssignee(TaskService.InvalidAssigneeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The assignee must be a current member of this team.");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Use a nonblank title of at most 200 characters, a description of at most 5000 characters, and valid status and priority values.");
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid JSON body with valid field values is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedFailure(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete task request.");
	}
}
