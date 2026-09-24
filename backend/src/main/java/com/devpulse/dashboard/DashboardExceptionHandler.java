package com.devpulse.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.devpulse.project.ProjectService;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler extends ResponseEntityExceptionHandler {
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail denied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Team membership is required to view this dashboard.");
	}
	@ExceptionHandler(ProjectService.MissingProjectException.class)
	public ProblemDetail missing(ProjectService.MissingProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Project not found in this team.");
	}
	@ExceptionHandler(ProjectService.ArchivedProjectException.class)
	public ProblemDetail archived(ProjectService.ArchivedProjectException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This project is archived. Select an active project.");
	}
	@ExceptionHandler(Exception.class)
	public ProblemDetail unexpected(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load dashboard.");
	}
}