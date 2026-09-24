package com.devpulse.team;

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

@RestControllerAdvice(assignableTypes = TeamController.class)
public class TeamExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission for this team action.");
	}

	@ExceptionHandler(TeamService.OwnerRemovalException.class)
	public ProblemDetail handleOwnerRemoval(TeamService.OwnerRemovalException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The team owner cannot be removed.");
	}

	@ExceptionHandler(TeamService.MemberNotFoundException.class)
	public ProblemDetail handleMissingMember(TeamService.MemberNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Team member not found.");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A team name of 1 to 100 nonblank characters is required.");
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid JSON request body is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedFailure(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete team request.");
	}
}