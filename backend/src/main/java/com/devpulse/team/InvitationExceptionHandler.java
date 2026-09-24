package com.devpulse.team;

import org.springframework.beans.TypeMismatchException;
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

@RestControllerAdvice(assignableTypes = InvitationController.class)
public class InvitationExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Only the team owner can manage invitations.");
	}

	@ExceptionHandler(InvitationService.InvalidInvitationException.class)
	public ProblemDetail handleInvalidInvitation(InvitationService.InvalidInvitationException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Invitation is invalid, expired, revoked, or already used.");
	}

	@ExceptionHandler(InvitationService.AlreadyTeamMemberException.class)
	public ProblemDetail handleExistingMember(InvitationService.AlreadyTeamMemberException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "You are already a member of this team.");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid invitation request.");
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid UUID is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid JSON request body is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedFailure(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete invitation request.");
	}
}