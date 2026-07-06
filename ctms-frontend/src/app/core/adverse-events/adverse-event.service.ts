import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AdverseEventResponse {
  id: number;
  subjectId: number;
  subjectCode: string;
  visitId: number | null;
  description: string;
  severity: 'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING';
  status: 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED';
  resolutionNotes: string | null;
  resolvedAt: string | null;
  createdByUsername: string;
  createdAt: string;
}

export interface ReportAdverseEventRequest {
  subjectId: number;
  visitId: number | null;
  description: string;
  severity: string;
}

@Injectable({ providedIn: 'root' })
export class AdverseEventService {
  constructor(private readonly http: HttpClient) {}

  list(subjectId: number): Observable<AdverseEventResponse[]> {
    return this.http.get<AdverseEventResponse[]>('/api/adverse-events', { params: { subjectId } });
  }

  board(): Observable<AdverseEventResponse[]> {
    return this.http.get<AdverseEventResponse[]>('/api/adverse-events/board');
  }

  report(req: ReportAdverseEventRequest): Observable<AdverseEventResponse> {
    return this.http.post<AdverseEventResponse>('/api/adverse-events', req);
  }

  transition(id: number, targetStatus: string, justification: string): Observable<AdverseEventResponse> {
    return this.http.post<AdverseEventResponse>(`/api/adverse-events/${id}/transition`, { targetStatus, justification });
  }

  resolve(id: number, resolutionNotes: string, password: string): Observable<AdverseEventResponse> {
    return this.http.post<AdverseEventResponse>(`/api/adverse-events/${id}/resolve`, { resolutionNotes, password });
  }
}
