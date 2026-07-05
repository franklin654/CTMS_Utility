import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface EligibilityCriterionResponse {
  id: number;
  studyId: number;
  label: string;
  criterionType: 'INCLUSION' | 'EXCLUSION';
  active: boolean;
}

export interface CreateEligibilityCriterionRequest {
  studyId: number;
  label: string;
  criterionType: 'INCLUSION' | 'EXCLUSION';
}

@Injectable({ providedIn: 'root' })
export class EligibilityCriterionService {
  constructor(private readonly http: HttpClient) {}

  list(studyId: number): Observable<EligibilityCriterionResponse[]> {
    return this.http.get<EligibilityCriterionResponse[]>('/api/eligibility-criteria', { params: { studyId } });
  }

  create(req: CreateEligibilityCriterionRequest): Observable<EligibilityCriterionResponse> {
    return this.http.post<EligibilityCriterionResponse>('/api/eligibility-criteria', req);
  }

  deactivate(id: number): Observable<EligibilityCriterionResponse> {
    return this.http.put<EligibilityCriterionResponse>(`/api/eligibility-criteria/${id}/deactivate`, {});
  }
}
