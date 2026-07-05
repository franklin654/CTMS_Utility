import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  templateUrl: './forgot-password.component.html',
})
export class ForgotPasswordComponent {
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  readonly submitted = signal(false);

  constructor(private readonly authService: AuthService) {}

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    // Always show the same confirmation regardless of outcome -- the backend is deliberately
    // silent on unknown emails to avoid user enumeration, so the UI must be too.
    this.authService.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.submitted.set(true),
      error: () => this.submitted.set(true),
    });
  }
}
