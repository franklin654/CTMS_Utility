import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SubjectResponse } from '../subjects/subject.service';

export interface UpdateOwnProfileRequest {
  contactPhone: string | null;
  contactEmail: string | null;
  address: string | null;
  emergencyContact: string | null;
}

/** Epic 10 Story 05 -- deliberately narrow: only contact fields are editable here. dateOfBirth,
 * gender, medicalHistory, and status stay staff-only and are never sent in the update request. */
@Injectable({ providedIn: 'root' })
export class PatientProfileService {
  constructor(private readonly http: HttpClient) {}

  get(): Observable<SubjectResponse> {
    return this.http.get<SubjectResponse>('/api/patient/profile');
  }

  update(req: UpdateOwnProfileRequest): Observable<SubjectResponse> {
    return this.http.put<SubjectResponse>('/api/patient/profile', req);
  }
}
