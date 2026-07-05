import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface MilestoneResponse {
  id: number;
  studyId: number;
  studyCode: string;
  milestoneType: 'FPI' | 'LPI' | 'LPO' | 'DBL';
  plannedDate: string;
  actualDate: string | null;
  delayed: boolean;
  createdByUsername: string;
  createdAt: string;
}

export interface CreateMilestoneRequest {
  studyId: number;
  milestoneType: string;
  plannedDate: string;
}

@Injectable({ providedIn: 'root' })
export class MilestoneService {
  constructor(private readonly http: HttpClient) {}

  listByStudy(studyId: number): Observable<MilestoneResponse[]> {
    return this.http.get<MilestoneResponse[]>('/api/milestones', { params: { studyId } });
  }

  create(req: CreateMilestoneRequest): Observable<MilestoneResponse> {
    return this.http.post<MilestoneResponse>('/api/milestones', req);
  }

  updatePlannedDate(id: number, plannedDate: string): Observable<MilestoneResponse> {
    return this.http.put<MilestoneResponse>(`/api/milestones/${id}`, { plannedDate });
  }

  recordActual(id: number, actualDate: string): Observable<MilestoneResponse> {
    return this.http.post<MilestoneResponse>(`/api/milestones/${id}/record-actual`, { actualDate });
  }
}
