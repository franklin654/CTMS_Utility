import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { DocumentResponse, DocumentService } from '../../../core/documents/document.service';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [MatButtonModule, MatPaginatorModule, RouterLink, HasRoleDirective, StatusChipPipe],
  templateUrl: './document-list.component.html',
})
export class DocumentListComponent implements OnInit {
  readonly documents = signal<DocumentResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);

  constructor(private readonly documentService: DocumentService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.documentService.list(this.pageIndex(), this.pageSize()).subscribe((page) => {
      this.documents.set(page.content);
      this.totalElements.set(page.totalElements);
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }
}
