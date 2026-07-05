import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface VisitTemplateResponse {
  id: number;
  studyId: number;
  name: string;
  sequenceNumber: number;
  targetDay: number;
  windowEarlyDays: number;
  windowLateDays: number;
  requiredProcedures: string | null;
  visitType: 'ONSITE' | 'REMOTE';
  active: boolean;
}

export interface CreateVisitTemplateRequest {
  studyId: number;
  name: string;
  sequenceNumber: number;
  targetDay: number;
  windowEarlyDays: number;
  windowLateDays: number;
  requiredProcedures: string | null;
  visitType: 'ONSITE' | 'REMOTE';
}

export type UpdateVisitTemplateRequest = Omit<CreateVisitTemplateRequest, 'studyId'>;

@Injectable({ providedIn: 'root' })
export class VisitTemplateService {
  constructor(private readonly http: HttpClient) {}

  list(studyId: number): Observable<VisitTemplateResponse[]> {
    return this.http.get<VisitTemplateResponse[]>('/api/visit-templates', { params: { studyId } });
  }

  create(req: CreateVisitTemplateRequest): Observable<VisitTemplateResponse> {
    return this.http.post<VisitTemplateResponse>('/api/visit-templates', req);
  }

  update(id: number, req: UpdateVisitTemplateRequest): Observable<VisitTemplateResponse> {
    return this.http.put<VisitTemplateResponse>(`/api/visit-templates/${id}`, req);
  }

  deactivate(id: number): Observable<VisitTemplateResponse> {
    return this.http.post<VisitTemplateResponse>(`/api/visit-templates/${id}/deactivate`, {});
  }
}
