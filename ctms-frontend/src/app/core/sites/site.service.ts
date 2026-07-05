import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface SiteResponse {
  id: number;
  studyId: number;
  studyCode: string;
  siteCode: string;
  name: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  stateProvince: string | null;
  postalCode: string | null;
  country: string;
  principalInvestigatorName: string;
  principalInvestigatorContact: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  feasibilityStatus: string;
  regulatoryInformation: string | null;
  status: 'PENDING_ACTIVATION' | 'ACTIVE';
  activationDate: string | null;
  assignedCraUsername: string | null;
  createdByUsername: string;
  modifiedByUsername: string;
  createdAt: string;
  modifiedAt: string;
}

export interface ChecklistItemResponse {
  id: number;
  itemType: 'FEASIBILITY_COMPLETION' | 'IRB_EC_APPROVAL' | 'CONTRACT_COMPLETION' | 'ESSENTIAL_DOCUMENTS_SUBMISSION' | 'SITE_INITIATION_VISIT';
  status: 'PENDING' | 'COMPLETE';
  completedDate: string | null;
  note: string | null;
  updatedByUsername: string | null;
  updatedAt: string;
}

export interface CreateSiteRequest {
  studyId: number;
  siteCode: string;
  name: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  stateProvince: string | null;
  postalCode: string | null;
  country: string;
  principalInvestigatorName: string;
  principalInvestigatorContact: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  feasibilityStatus: string;
  regulatoryInformation: string | null;
}

export type UpdateSiteRequest = Omit<CreateSiteRequest, 'studyId' | 'siteCode'>;

export interface UpdateChecklistItemRequest {
  status: 'PENDING' | 'COMPLETE';
  completedDate: string | null;
  note: string | null;
}

export interface ActivationAttemptResponse {
  activated: boolean;
  missingItems: string[];
  site: SiteResponse;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SiteService {
  constructor(private readonly http: HttpClient) {}

  list(studyId?: number, search?: string, page = 0, size = 20): Observable<Page<SiteResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (studyId != null) {
      params['studyId'] = studyId;
    }
    if (search) {
      params['search'] = search;
    }
    return this.http.get<Page<SiteResponse>>('/api/sites', { params });
  }

  get(id: number): Observable<SiteResponse> {
    return this.http.get<SiteResponse>(`/api/sites/${id}`);
  }

  create(req: CreateSiteRequest): Observable<SiteResponse> {
    return this.http.post<SiteResponse>('/api/sites', req);
  }

  update(id: number, req: UpdateSiteRequest): Observable<SiteResponse> {
    return this.http.put<SiteResponse>(`/api/sites/${id}`, req);
  }

  assignCra(id: number, craUsername: string): Observable<SiteResponse> {
    return this.http.put<SiteResponse>(`/api/sites/${id}/cra`, { craUsername });
  }

  checklist(id: number): Observable<ChecklistItemResponse[]> {
    return this.http.get<ChecklistItemResponse[]>(`/api/sites/${id}/checklist`);
  }

  updateChecklistItem(id: number, itemType: string, req: UpdateChecklistItemRequest): Observable<ChecklistItemResponse> {
    return this.http.put<ChecklistItemResponse>(`/api/sites/${id}/checklist/${itemType}`, req);
  }

  attemptActivation(id: number): Observable<ActivationAttemptResponse> {
    return this.http.post<ActivationAttemptResponse>(`/api/sites/${id}/attempt-activation`, {});
  }
}
