import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { StudyResponse, StudyService } from '../../../core/studies/study.service';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';

@Component({
  selector: 'app-study-list',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatPaginatorModule, RouterLink, HasRoleDirective, StatusChipPipe],
  templateUrl: './study-list.component.html',
})
export class StudyListComponent implements OnInit {
  readonly search = new FormControl('', { nonNullable: true });
  readonly studies = signal<StudyResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);

  constructor(private readonly studyService: StudyService) {}

  ngOnInit(): void {
    this.load();
    this.search.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
  }

  load(): void {
    this.studyService.list(this.search.value, this.pageIndex(), this.pageSize()).subscribe((page) => {
      this.studies.set(page.content);
      this.totalElements.set(page.totalElements);
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }
}
