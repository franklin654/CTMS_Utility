import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface PaymentResponse {
  id: number;
  studyId: number;
  studyCode: string;
  siteId: number | null;
  siteCode: string | null;
  costCategory: string;
  eventCode: string;
  triggerEntityName: string;
  triggerEntityId: number;
  baseAmount: number;
  multiplier: number;
  capAmount: number | null;
  amount: number;
  currency: string;
  status: 'PENDING' | 'ON_HOLD' | 'RELEASED';
  holdReason: string | null;
  heldAt: string | null;
  heldByUsername: string | null;
  releaseReason: string | null;
  releasedAt: string | null;
  releasedByUsername: string | null;
  createdByUsername: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PaymentFilters {
  studyId?: number | null;
  siteId?: number | null;
  costCategory?: string | null;
  status?: string | null;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  constructor(private readonly http: HttpClient) {}

  list(filters: PaymentFilters, page = 0, size = 20): Observable<Page<PaymentResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (filters.studyId != null) params['studyId'] = filters.studyId;
    if (filters.siteId != null) params['siteId'] = filters.siteId;
    if (filters.costCategory) params['costCategory'] = filters.costCategory;
    if (filters.status) params['status'] = filters.status;
    return this.http.get<Page<PaymentResponse>>('/api/payments', { params });
  }

  get(id: number): Observable<PaymentResponse> {
    return this.http.get<PaymentResponse>(`/api/payments/${id}`);
  }

  hold(id: number, reason: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`/api/payments/${id}/hold`, { reason });
  }

  release(id: number, reason: string, password: string): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`/api/payments/${id}/release`, { reason, password });
  }
}
