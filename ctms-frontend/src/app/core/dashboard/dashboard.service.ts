import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MilestoneResponse } from '../milestones/milestone.service';

export interface HighRiskSiteResponse {
  siteId: number;
  siteCode: string;
  name: string;
  missedVisitRatePercent: number;
  openHighSeverityAeCount: number;
}

export interface DashboardSummaryResponse {
  enrollmentByStatus: Record<string, number>;
  siteActivationByStatus: Record<string, number>;
  visitAdherenceRatePercent: number | null;
  sitesByCountry: Record<string, number>;
  highRiskSites: HighRiskSiteResponse[];
  milestones: MilestoneResponse[];
}

export interface DashboardFilters {
  studyId?: number | null;
  country?: string | null;
  siteId?: number | null;
  phase?: string | null;
}

export interface DashboardFilterOptionsResponse {
  countries: string[];
  phases: string[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  constructor(private readonly http: HttpClient) {}

  summary(filters: DashboardFilters): Observable<DashboardSummaryResponse> {
    return this.http.get<DashboardSummaryResponse>('/api/dashboard/summary', { params: this.toParams(filters) });
  }

  filterOptions(): Observable<DashboardFilterOptionsResponse> {
    return this.http.get<DashboardFilterOptionsResponse>('/api/dashboard/filter-options');
  }

  export(filters: DashboardFilters, format: 'pdf' | 'excel'): Observable<Blob> {
    return this.http.get('/api/dashboard/export', {
      params: { ...this.toParams(filters), format },
      responseType: 'blob',
    });
  }

  private toParams(filters: DashboardFilters): Record<string, string | number> {
    const params: Record<string, string | number> = {};
    if (filters.studyId != null) params['studyId'] = filters.studyId;
    if (filters.country) params['country'] = filters.country;
    if (filters.siteId != null) params['siteId'] = filters.siteId;
    if (filters.phase) params['phase'] = filters.phase;
    return params;
  }
}
