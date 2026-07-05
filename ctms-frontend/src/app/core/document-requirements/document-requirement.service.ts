import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface DocumentRequirementResponse {
  id: number;
  studyId: number;
  studyCode: string;
  studyPhase: string;
  documentCategory: string;
  mandatory: boolean;
  createdByUsername: string;
}

export interface CreateDocumentRequirementRequest {
  studyId: number;
  studyPhase: string;
  documentCategory: string;
  mandatory: boolean;
}

@Injectable({ providedIn: 'root' })
export class DocumentRequirementService {
  constructor(private readonly http: HttpClient) {}

  listByStudy(studyId: number): Observable<DocumentRequirementResponse[]> {
    return this.http.get<DocumentRequirementResponse[]>('/api/document-requirements', { params: { studyId } });
  }

  create(req: CreateDocumentRequirementRequest): Observable<DocumentRequirementResponse> {
    return this.http.post<DocumentRequirementResponse>('/api/document-requirements', req);
  }
}
