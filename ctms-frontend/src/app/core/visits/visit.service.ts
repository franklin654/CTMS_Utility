import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface VisitResponse {
  id: number;
  subjectId: number;
  visitTemplateId: number | null;
  adHoc: boolean;
  name: string;
  sequenceNumber: number;
  targetDay: number;
  windowEarlyDays: number;
  windowLateDays: number;
  requiredProcedures: string | null;
  visitType: 'ONSITE' | 'REMOTE';
  scheduledDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'MISSED' | 'RESCHEDULED';
  actualDate: string | null;
  actualTime: string | null;
  notes: string | null;
  reasonCode: string | null;
  rescheduledFromVisitId: number | null;
  completedAt: string | null;
}

export interface SubjectVisitScheduleResponse {
  visits: VisitResponse[];
  complianceRate: number;
}

export interface MarkVisitCompletedRequest {
  actualDate: string;
  actualTime: string | null;
  notes: string | null;
}

export interface MarkVisitMissedRequest {
  reasonCode: string;
}

export interface RescheduleVisitRequest {
  newDate: string;
  reasonCode: string;
}

export interface CreateAdHocVisitRequest {
  name: string;
  scheduledDate: string;
  visitType: 'ONSITE' | 'REMOTE';
  requiredProcedures: string | null;
  reasonCode: string;
}

@Injectable({ providedIn: 'root' })
export class VisitService {
  constructor(private readonly http: HttpClient) {}

  schedule(subjectId: number): Observable<SubjectVisitScheduleResponse> {
    return this.http.get<SubjectVisitScheduleResponse>(`/api/subjects/${subjectId}/visits`);
  }

  scheduleAdHoc(subjectId: number, req: CreateAdHocVisitRequest): Observable<VisitResponse> {
    return this.http.post<VisitResponse>(`/api/subjects/${subjectId}/visits/ad-hoc`, req);
  }

  complete(id: number, req: MarkVisitCompletedRequest): Observable<VisitResponse> {
    return this.http.post<VisitResponse>(`/api/visits/${id}/complete`, req);
  }

  miss(id: number, req: MarkVisitMissedRequest): Observable<VisitResponse> {
    return this.http.post<VisitResponse>(`/api/visits/${id}/miss`, req);
  }

  reschedule(id: number, req: RescheduleVisitRequest): Observable<VisitResponse> {
    return this.http.post<VisitResponse>(`/api/visits/${id}/reschedule`, req);
  }
}
