import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RuleSetDetailResponse, RuleSetService } from '../../../../core/rule-sets/rule-set.service';

@Component({
  selector: 'app-rule-set-detail',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, DatePipe],
  templateUrl: './rule-set-detail.component.html',
})
export class RuleSetDetailComponent implements OnInit {
  readonly detail = signal<RuleSetDetailResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly drlControl = new FormControl('', { nonNullable: true });

  private name = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly ruleSetService: RuleSetService,
  ) {}

  ngOnInit(): void {
    this.name = this.route.snapshot.paramMap.get('name')!;
    this.load();
  }

  load(): void {
    this.errorMessage.set(null);
    this.ruleSetService.getDetail(this.name).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        const active = detail.definitions.find((d) => d.active);
        this.drlControl.setValue(active?.drlContent ?? '');
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load rule set.'),
    });
  }

  saveNewVersion(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.ruleSetService.addDefinition(this.name, this.drlControl.value).subscribe({
      next: () => {
        this.successMessage.set('New version activated.');
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Failed to save -- check the DRL syntax.'),
    });
  }
}
