package com.ctms.ctms_backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the daily visit-alert sweep ({@code VisitAlertService.runDailyAlertSweep}) -- the
 * first {@code @Scheduled} job in this application. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
