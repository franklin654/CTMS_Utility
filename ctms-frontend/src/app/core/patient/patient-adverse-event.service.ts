import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AdverseEventResponse } from '../adverse-events/adverse-event.service';

export interface PatientReportAdverseEventRequest {
  description: string;
  severity: 'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING';
}

/** Closes the gap Phase 7 deferred to the Patient Portal -- reuses the existing AdverseEventService
 * unchanged server-side, lands in the same OPEN state staff already monitor via the AE board. */
@Injectable({ providedIn: 'root' })
export class PatientAdverseEventService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<AdverseEventResponse[]> {
    return this.http.get<AdverseEventResponse[]>('/api/patient/adverse-events');
  }

  report(req: PatientReportAdverseEventRequest): Observable<AdverseEventResponse> {
    return this.http.post<AdverseEventResponse>('/api/patient/adverse-events', req);
  }
}
