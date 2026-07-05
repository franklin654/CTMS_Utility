import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: Validators.required }),
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
  ) {}

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const { username, password } = this.form.getRawValue();

    this.authService.login(username, password).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (res.mustChangePassword) {
          this.router.navigate(['/change-password']);
          return;
        }
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.submitting.set(false);
        if (err.status === 423) {
          this.errorMessage.set('Account locked due to repeated failed attempts. Try again later.');
        } else if (err.status === 401) {
          this.errorMessage.set('Invalid username or password.');
        } else {
          this.errorMessage.set('Something went wrong. Please try again.');
        }
      },
    });
  }
}
