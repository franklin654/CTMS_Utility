import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ProtocolDeviationResponse {
  id: number;
  subjectId: number;
  subjectCode: string;
  description: string;
  severity: 'MINOR' | 'MAJOR' | 'CRITICAL';
  deviationDate: string;
  createdByUsername: string;
  createdAt: string;
}

export interface ReportProtocolDeviationRequest {
  subjectId: number;
  description: string;
  severity: string;
  deviationDate: string;
}

@Injectable({ providedIn: 'root' })
export class ProtocolDeviationService {
  constructor(private readonly http: HttpClient) {}

  list(subjectId: number): Observable<ProtocolDeviationResponse[]> {
    return this.http.get<ProtocolDeviationResponse[]>('/api/protocol-deviations', { params: { subjectId } });
  }

  report(req: ReportProtocolDeviationRequest): Observable<ProtocolDeviationResponse> {
    return this.http.post<ProtocolDeviationResponse>('/api/protocol-deviations', req);
  }
}
