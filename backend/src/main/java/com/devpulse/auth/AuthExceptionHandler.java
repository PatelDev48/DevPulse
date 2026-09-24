package com.devpulse.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalidInput(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid signup input.");
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ProblemDetail handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email is already registered.");
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A valid JSON request body is required.");
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedFailure(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete authentication request.");
	}
}