import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface PortalAccountResponse {
  username: string;
  temporaryPassword: string;
}

export interface SubjectResponse {
  id: number;
  subjectCode: string;
  studyId: number;
  studyCode: string;
  siteId: number;
  siteCode: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  address: string | null;
  emergencyContact: string | null;
  notes: string | null;
  medicalHistory: string | null;
  screeningDate: string;
  status: 'SCREENED' | 'ENROLLED' | 'IN_TREATMENT' | 'COMPLETED' | 'WITHDRAWN';
  createdByUsername: string;
  modifiedByUsername: string;
  createdAt: string;
  modifiedAt: string;
}

export interface SubjectStatusHistoryResponse {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  reasonCode: string | null;
  changedByUsername: string;
  changedAt: string;
}

export interface EligibilityAnswerRequest {
  criterionId: number;
  met: boolean;
}

export interface EnrollSubjectRequest {
  studyId: number;
  siteId: number;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  address: string | null;
  emergencyContact: string | null;
  notes: string | null;
  medicalHistory: string | null;
  screeningDate: string;
  eligibilityAnswers: EligibilityAnswerRequest[];
}

export type UpdateSubjectRequest = Omit<
  EnrollSubjectRequest,
  'studyId' | 'siteId' | 'screeningDate' | 'eligibilityAnswers' | 'dateOfBirth'
>;

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SubjectService {
  constructor(private readonly http: HttpClient) {}

  list(studyId?: number, siteId?: number, search?: string, page = 0, size = 20): Observable<Page<SubjectResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (studyId != null) {
      params['studyId'] = studyId;
    }
    if (siteId != null) {
      params['siteId'] = siteId;
    }
    if (search) {
      params['search'] = search;
    }
    return this.http.get<Page<SubjectResponse>>('/api/subjects', { params });
  }

  get(id: number): Observable<SubjectResponse> {
    return this.http.get<SubjectResponse>(`/api/subjects/${id}`);
  }

  enroll(req: EnrollSubjectRequest): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>('/api/subjects', req);
  }

  update(id: number, req: UpdateSubjectRequest): Observable<SubjectResponse> {
    return this.http.put<SubjectResponse>(`/api/subjects/${id}`, req);
  }

  transition(id: number, targetStatus: string, justification: string): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>(`/api/subjects/${id}/transition`, { targetStatus, justification });
  }

  withdraw(id: number, reasonCode: string, password: string): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>(`/api/subjects/${id}/withdraw`, { reasonCode, password });
  }

  history(id: number): Observable<SubjectStatusHistoryResponse[]> {
    return this.http.get<SubjectStatusHistoryResponse[]>(`/api/subjects/${id}/history`);
  }

  createPortalAccount(id: number): Observable<PortalAccountResponse> {
    return this.http.post<PortalAccountResponse>(`/api/subjects/${id}/portal-account`, {});
  }

  resetPortalPassword(id: number): Observable<PortalAccountResponse> {
    return this.http.post<PortalAccountResponse>(`/api/subjects/${id}/portal-account/reset-password`, {});
  }
}
