import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DocumentResponse } from '../documents/document.service';

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** Epic 10 Story 04 -- list is scoped to documents this patient personally uploaded (never other
 * patients' uploads, even within the same study). Uploads start PENDING_REVIEW, not CURRENT, until
 * staff reviews via the existing approval workflow -- currentVersion stays null until then. */
@Injectable({ providedIn: 'root' })
export class PatientDocumentService {
  constructor(private readonly http: HttpClient) {}

  list(page = 0, size = 20): Observable<Page<DocumentResponse>> {
    return this.http.get<Page<DocumentResponse>>('/api/patient/documents', { params: { page, size } });
  }

  upload(category: string, title: string, effectiveDate: string | null, file: File): Observable<DocumentResponse> {
    const formData = new FormData();
    formData.append('category', category);
    formData.append('title', title);
    if (effectiveDate) {
      formData.append('effectiveDate', effectiveDate);
    }
    formData.append('file', file);
    return this.http.post<DocumentResponse>('/api/patient/documents', formData);
  }
}
