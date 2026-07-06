import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RuleSetDetailResponse, RuleSetService } from '../../../../core/rule-sets/rule-set.service';

/** Plain-language explanation + a minimal, always-valid example DRL snippet per rule-set
 * category, shown above the raw DRL editor -- this system deliberately keeps rules as
 * inspectable/explainable DRL text rather than a black-box model (CLAUDE.md #2.1), but raw DRL
 * is still opaque to a non-developer without this context. */
export interface RuleSetCategoryDoc {
  explanation: string;
  factClass: string;
  factFields: string[];
  example: string;
}

export const RULE_SET_CATEGORY_DOCS: Record<string, RuleSetCategoryDoc> = {
  ELIGIBILITY: {
    explanation:
      'Evaluated once per subject enrollment attempt, one rule firing per answered eligibility criterion. ' +
      'Each rule inspects a single criterion answer and, if it disqualifies the subject, adds a human-readable ' +
      'reason to the results list -- any non-empty results list blocks enrollment.',
    factClass: 'EligibilityAnswerFact',
    factFields: ['criterionType ("INCLUSION" or "EXCLUSION")', 'met (boolean)', 'label (the criterion\'s display text)'],
    example:
      'rule "Unmet inclusion criterion"\n' +
      'when\n' +
      '    $f : EligibilityAnswerFact(criterionType == "INCLUSION", met == false)\n' +
      'then\n' +
      '    results.add("Inclusion criterion not met: " + $f.getLabel());\n' +
      'end',
  },
  WORKFLOW: {
    explanation:
      'Evaluated whenever a trigger event fires (e.g. a subject is enrolled, a site is activated). Each rule ' +
      'matches on the event code and, if it matches, adds a task outcome describing the SLA hours, the owning ' +
      'role, the escalation role, and the priority for the task that should be auto-created.',
    factClass: 'TaskTriggerFact',
    factFields: ['eventCode (e.g. "SUBJECT_ENROLLED", "SITE_ACTIVATED")'],
    example:
      'rule "Subject enrolled task rule"\n' +
      'when\n' +
      '    $f : TaskTriggerFact(eventCode == "SUBJECT_ENROLLED")\n' +
      'then\n' +
      '    results.add(new TaskRuleOutcome(48, "SITE_COORDINATOR", "STUDY_MANAGER", "MEDIUM"));\n' +
      'end',
  },
  PAYMENT: {
    explanation:
      'Evaluated whenever a payment-triggering event fires (e.g. a monitoring visit or milestone completes). ' +
      'Each rule matches on the event code and, if it matches, adds a payment outcome describing the cost ' +
      'category, base amount, multiplier, an optional cap, and currency for the payment to auto-generate.',
    factClass: 'PaymentTriggerFact',
    factFields: ['eventCode (e.g. "VISIT_COMPLETED", "SITE_ACTIVATED", "MILESTONE_REACHED_FPI")'],
    example:
      'rule "Visit completed payment rule"\n' +
      'when\n' +
      '    $f : PaymentTriggerFact(eventCode == "VISIT_COMPLETED")\n' +
      'then\n' +
      '    results.add(new PaymentRuleOutcome("MONITORING", new java.math.BigDecimal("500.00"), new java.math.BigDecimal("1.0"), null, "USD"));\n' +
      'end',
  },
};

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

  get categoryDoc(): RuleSetCategoryDoc | null {
    const category = this.detail()?.category;
    return category ? (RULE_SET_CATEGORY_DOCS[category] ?? null) : null;
  }

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
