import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface RuleSetSummaryResponse {
  id: number;
  name: string;
  category: string;
  active: boolean;
  latestVersion: number;
}

export interface RuleDefinitionDetailResponse {
  id: number;
  version: number;
  active: boolean;
  drlContent: string;
  createdAt: string;
}

export interface RuleSetDetailResponse {
  id: number;
  name: string;
  category: string;
  description: string | null;
  active: boolean;
  definitions: RuleDefinitionDetailResponse[];
}

@Injectable({ providedIn: 'root' })
export class RuleSetService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<RuleSetSummaryResponse[]> {
    return this.http.get<RuleSetSummaryResponse[]>('/api/rule-sets');
  }

  getDetail(name: string): Observable<RuleSetDetailResponse> {
    return this.http.get<RuleSetDetailResponse>(`/api/rule-sets/${name}`);
  }

  addDefinition(name: string, drlContent: string): Observable<RuleDefinitionDetailResponse> {
    return this.http.post<RuleDefinitionDetailResponse>(`/api/rule-sets/${name}/definitions`, { drlContent });
  }
}
