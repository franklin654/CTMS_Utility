package com.ctms.ctms_backend.subject.dto;

/** BL Epic 10 Story 05 (Update Patient Profile). Deliberately narrow -- only contact/demographic
 * fields the patient is allowed to self-edit. dateOfBirth, gender, medicalHistory, status, and
 * study/site assignment stay staff-only (SubjectService.updateSubject), matching the BRD's
 * explicit DOB lock and "sensitive fields locked" framing. */
public record UpdateOwnProfileRequest(String contactPhone, String contactEmail, String address, String emergencyContact) {}
