import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface TaskResponse {
  id: number;
  eventCode: string;
  title: string;
  description: string | null;
  entityName: string;
  entityId: number;
  ownerUsername: string;
  ownerRole: string;
  escalationTargetUsername: string;
  escalationRole: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  status: 'OPEN' | 'IN_PROGRESS' | 'COMPLETED';
  dueAt: string;
  escalated: boolean;
  escalatedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class TaskService {
  constructor(private readonly http: HttpClient) {}

  myTasks(page = 0, size = 20): Observable<Page<TaskResponse>> {
    return this.http.get<Page<TaskResponse>>('/api/tasks', { params: { page, size } });
  }

  allTasks(page = 0, size = 20): Observable<Page<TaskResponse>> {
    return this.http.get<Page<TaskResponse>>('/api/tasks/all', { params: { page, size } });
  }

  start(id: number): Observable<TaskResponse> {
    return this.http.post<TaskResponse>(`/api/tasks/${id}/start`, {});
  }

  complete(id: number): Observable<TaskResponse> {
    return this.http.post<TaskResponse>(`/api/tasks/${id}/complete`, {});
  }
}
