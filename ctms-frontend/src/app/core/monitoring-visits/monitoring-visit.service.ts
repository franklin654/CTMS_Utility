import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface MonitoringVisitResponse {
  id: number;
  siteId: number;
  siteCode: string;
  craUsername: string;
  visitType: 'SIV' | 'IMV' | 'COV';
  visitDate: string;
  findings: string | null;
  issuesIdentified: string | null;
  checklistNotes: string | null;
  createdByUsername: string;
  createdAt: string;
  modifiedByUsername: string;
  modifiedAt: string;
}

export interface LogMonitoringVisitRequest {
  siteId: number;
  visitType: string;
  visitDate: string;
  findings: string | null;
  issuesIdentified: string | null;
  checklistNotes: string | null;
}

export interface MonitoringVisitReportResponse {
  id: number;
  monitoringVisitId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByUsername: string;
  uploadedAt: string;
}

@Injectable({ providedIn: 'root' })
export class MonitoringVisitService {
  constructor(private readonly http: HttpClient) {}

  list(siteId: number): Observable<MonitoringVisitResponse[]> {
    return this.http.get<MonitoringVisitResponse[]>('/api/monitoring-visits', { params: { siteId } });
  }

  log(req: LogMonitoringVisitRequest): Observable<MonitoringVisitResponse> {
    return this.http.post<MonitoringVisitResponse>('/api/monitoring-visits', req);
  }

  update(id: number, req: LogMonitoringVisitRequest): Observable<MonitoringVisitResponse> {
    return this.http.put<MonitoringVisitResponse>(`/api/monitoring-visits/${id}`, req);
  }

  reports(monitoringVisitId: number): Observable<MonitoringVisitReportResponse[]> {
    return this.http.get<MonitoringVisitReportResponse[]>(`/api/monitoring-visits/${monitoringVisitId}/reports`);
  }

  uploadReport(monitoringVisitId: number, file: File): Observable<MonitoringVisitReportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<MonitoringVisitReportResponse>(`/api/monitoring-visits/${monitoringVisitId}/reports`, formData);
  }

  downloadReport(reportId: number): Observable<Blob> {
    return this.http.get(`/api/monitoring-visit-reports/${reportId}/download`, { responseType: 'blob' });
  }
}
