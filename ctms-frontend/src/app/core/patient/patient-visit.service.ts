import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SubjectVisitScheduleResponse } from '../visits/visit.service';

/** Epic 10 Story 02 -- always the caller's own schedule; the backend resolves "which subject" from
 * the JWT identity, never a client-supplied ID. */
@Injectable({ providedIn: 'root' })
export class PatientVisitService {
  constructor(private readonly http: HttpClient) {}

  mySchedule(): Observable<SubjectVisitScheduleResponse> {
    return this.http.get<SubjectVisitScheduleResponse>('/api/patient/visits');
  }
}
