package com.devpulse.project;

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

@RestControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ProjectService.MissingProjectException.class)
	public ProblemDetail handleMissingProject(ProjectService.MissingProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project not found in this team.");
	}

	@ExceptionHandler(ProjectService.ArchivedProjectException.class)
	public ProblemDetail handleArchivedProject(ProjectService.ArchivedProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This project is archived. Restore it before making changes.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission for this project action.");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"A project name of 1 to 100 nonblank characters and a description of at most 2000 characters are required.");
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid JSON request body is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedFailure(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete project request.");
	}
}
