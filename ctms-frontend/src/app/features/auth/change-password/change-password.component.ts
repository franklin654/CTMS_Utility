import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './change-password.component.html',
})
export class ChangePasswordComponent {
  readonly form = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
    newPassword: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (err) => {
        this.errorMessage.set(
          err.error?.violations?.join('; ') ?? err.error?.message ?? 'Could not change password.',
        );
      },
    });
  }
}
