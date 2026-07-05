package com.ctms.ctms_backend.task.controller;

import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.task.dto.TaskResponse;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";
    private static final String OVERSIGHT_ROLES = "hasAnyRole('STUDY_MANAGER','ADMIN')";

    private final TaskService taskService;
    private final UserRepository userRepository;

    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<TaskResponse> myTasks(
            Principal principal,
            @PageableDefault(size = 20, sort = "dueAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return taskService.myTasks(currentUser(principal).getId(), pageable);
    }

    @GetMapping("/all")
    @PreAuthorize(OVERSIGHT_ROLES)
    public Page<TaskResponse> allTasks(
            @PageableDefault(size = 20, sort = "dueAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return taskService.allTasks(pageable);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(READ_ROLES)
    public TaskResponse start(Principal principal, @PathVariable Long id) {
        return taskService.start(id, principal.getName());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(READ_ROLES)
    public TaskResponse complete(Principal principal, @PathVariable Long id) {
        return taskService.complete(id, principal.getName());
    }

    private User currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow(InvalidCredentialsException::new);
    }
}
