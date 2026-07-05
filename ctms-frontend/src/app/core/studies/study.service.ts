import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface StudyResponse {
  id: number;
  studyCode: string;
  name: string;
  protocolId: string;
  protocolVersion: string;
  phase: string;
  sponsor: string;
  status: 'DRAFT' | 'ACTIVE' | 'CONDUCT' | 'CLOSEOUT';
  plannedStartDate: string | null;
  plannedEndDate: string | null;
  actualStartDate: string | null;
  actualEndDate: string | null;
  description: string | null;
  createdByUsername: string | null;
  modifiedByUsername: string | null;
  createdAt: string;
  modifiedAt: string;
}

export interface StudyStatusHistoryResponse {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  justification: string;
  changedByUsername: string;
  changedAt: string;
  signed: boolean;
}

export interface CreateStudyRequest {
  name: string;
  protocolId: string;
  protocolVersion: string;
  phase: string;
  sponsor: string;
  plannedStartDate: string | null;
  plannedEndDate: string | null;
  description: string | null;
}

export interface UpdateStudyRequest {
  name: string;
  protocolId: string;
  protocolVersion: string;
  phase: string;
  sponsor: string;
  plannedStartDate: string | null;
  plannedEndDate: string | null;
  actualStartDate: string | null;
  actualEndDate: string | null;
  description: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class StudyService {
  constructor(private readonly http: HttpClient) {}

  list(search?: string, page = 0, size = 20): Observable<Page<StudyResponse>> {
    return this.http.get<Page<StudyResponse>>('/api/studies', {
      params: { search: search ?? '', page, size },
    });
  }

  get(id: number): Observable<StudyResponse> {
    return this.http.get<StudyResponse>(`/api/studies/${id}`);
  }

  create(req: CreateStudyRequest): Observable<StudyResponse> {
    return this.http.post<StudyResponse>('/api/studies', req);
  }

  update(id: number, req: UpdateStudyRequest): Observable<StudyResponse> {
    return this.http.put<StudyResponse>(`/api/studies/${id}`, req);
  }

  transition(id: number, targetStatus: string, justification: string): Observable<StudyResponse> {
    return this.http.post<StudyResponse>(`/api/studies/${id}/transition`, { targetStatus, justification });
  }

  closeout(id: number, password: string, reason: string): Observable<StudyResponse> {
    return this.http.post<StudyResponse>(`/api/studies/${id}/closeout`, { password, reason });
  }

  history(id: number): Observable<StudyStatusHistoryResponse[]> {
    return this.http.get<StudyStatusHistoryResponse[]>(`/api/studies/${id}/history`);
  }
}
