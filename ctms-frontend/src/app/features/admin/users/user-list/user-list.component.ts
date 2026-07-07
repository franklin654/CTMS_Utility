import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import {
  CreateUserResponse,
  UserManagementService,
  UserResponse,
} from '../../../../core/users/user-management.service';
import { StatusChipPipe } from '../../../../core/utils/status-chip.pipe';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    DatePipe,
    StatusChipPipe,
  ],
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {
  readonly users = signal<UserResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);
  readonly roleCodes = signal<string[]>([]);
  readonly errorMessage = signal<string | null>(null);

  readonly showCreateForm = signal(false);
  readonly createResult = signal<CreateUserResponse | null>(null);
  readonly createErrorMessage = signal<string | null>(null);

  readonly editingUserId = signal<number | null>(null);
  readonly editRolesControl = new FormControl<string[]>([], { nonNullable: true });

  readonly createForm = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: Validators.required }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    fullName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    roles: new FormControl<string[]>([], { nonNullable: true, validators: Validators.required }),
  });

  constructor(private readonly userManagementService: UserManagementService) {}

  ngOnInit(): void {
    this.userManagementService.listRoleCodes().subscribe((codes) => this.roleCodes.set(codes));
    this.load();
  }

  load(): void {
    this.userManagementService.list(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.totalElements.set(page.totalElements);
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load users.'),
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  openCreateForm(): void {
    this.createErrorMessage.set(null);
    this.createResult.set(null);
    this.createForm.reset({ username: '', email: '', fullName: '', roles: [] });
    this.showCreateForm.set(true);
  }

  cancelCreateForm(): void {
    this.showCreateForm.set(false);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      return;
    }
    this.createErrorMessage.set(null);
    this.userManagementService.create(this.createForm.getRawValue()).subscribe({
      next: (result) => {
        this.createResult.set(result);
        this.showCreateForm.set(false);
        this.load();
      },
      error: (err) => this.createErrorMessage.set(err.error?.message ?? 'Could not create user.'),
    });
  }

  dismissCreateResult(): void {
    this.createResult.set(null);
  }

  startEditRoles(user: UserResponse): void {
    this.editingUserId.set(user.id);
    this.editRolesControl.setValue(user.roles);
  }

  cancelEditRoles(): void {
    this.editingUserId.set(null);
  }

  saveRoles(userId: number): void {
    if (this.editRolesControl.invalid || this.editRolesControl.value.length === 0) {
      return;
    }
    this.errorMessage.set(null);
    this.userManagementService.updateRoles(userId, this.editRolesControl.value).subscribe({
      next: () => {
        this.editingUserId.set(null);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update roles.'),
    });
  }

  toggleEnabled(user: UserResponse): void {
    this.errorMessage.set(null);
    this.userManagementService.setEnabled(user.id, !user.enabled).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update user status.'),
    });
  }
}
