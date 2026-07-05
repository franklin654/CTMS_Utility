import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface BudgetLineItemRequest {
  costCategory: string;
  plannedAmount: number;
  currency: string;
}

export interface BudgetLineItemResponse {
  costCategory: string;
  plannedAmount: number;
  actualAmount: number | null;
  variance: number | null;
  currency: string;
}

export interface BudgetVersionResponse {
  id: number;
  studyId: number;
  studyCode: string;
  versionNumber: number;
  status: 'CURRENT' | 'SUPERSEDED';
  reason: string | null;
  lineItems: BudgetLineItemResponse[];
  createdByUsername: string;
  createdAt: string;
}

export interface CreateBudgetRequest {
  studyId: number;
  lineItems: BudgetLineItemRequest[];
}

export interface CreateBudgetVersionRequest {
  lineItems: BudgetLineItemRequest[];
  reason: string;
}

@Injectable({ providedIn: 'root' })
export class BudgetService {
  constructor(private readonly http: HttpClient) {}

  create(req: CreateBudgetRequest): Observable<BudgetVersionResponse> {
    return this.http.post<BudgetVersionResponse>('/api/budgets', req);
  }

  createNewVersion(studyId: number, req: CreateBudgetVersionRequest): Observable<BudgetVersionResponse> {
    return this.http.post<BudgetVersionResponse>(`/api/budgets/${studyId}/versions`, req);
  }

  getCurrentVersion(studyId: number): Observable<BudgetVersionResponse> {
    return this.http.get<BudgetVersionResponse>(`/api/budgets/${studyId}`);
  }

  listVersions(studyId: number): Observable<BudgetVersionResponse[]> {
    return this.http.get<BudgetVersionResponse[]>(`/api/budgets/${studyId}/versions`);
  }

  getVersion(studyId: number, versionNumber: number): Observable<BudgetVersionResponse> {
    return this.http.get<BudgetVersionResponse>(`/api/budgets/${studyId}/versions/${versionNumber}`);
  }

  export(studyId: number, format: 'pdf' | 'excel'): Observable<Blob> {
    return this.http.get(`/api/budgets/${studyId}/export`, { params: { format }, responseType: 'blob' });
  }
}
