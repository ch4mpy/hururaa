import { HttpErrorResponse } from '@angular/common/http';
import { ProblemType } from '@api/hururaa-api';
import { isValidationProblem, problemMessage } from './problem-messages';

const problem = (type: ProblemType, parameters: Record<string, unknown>) => ({
  type,
  title: 'x',
  status: 400,
  detail: 'x',
  instance: '/x',
  parameters,
});

describe('problemMessage', () => {
  it('has a message for every problem type the API reports', () => {
    for (const type of Object.values(ProblemType)) {
      expect(problemMessage(problem(type, {}))).toBeTruthy();
    }
  });

  it('interpolates the parameters', () => {
    expect(
      problemMessage(
        new HttpErrorResponse({
          status: 409,
          error: problem(ProblemType.APPLICATION_ROLES_STILL_GRANTED, {
            applicationId: 3,
            groups: 'escales-agents',
          }),
        }),
      ),
    ).toBe(
      "Des groupes attribuent encore des rôles de l'application (escales-agents) : retirez-les d'abord",
    );
  });

  it('is undefined for anything but a typed problem', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 403 }))).toBeUndefined();
    expect(problemMessage({ type: 'urn:other', parameters: {} })).toBeUndefined();
  });

  it('recognizes validation problems', () => {
    expect(isValidationProblem(problem(ProblemType.VALIDATION, {}))).toBe(true);
    expect(isValidationProblem(problem(ProblemType.NOT_A_MEMBER, {}))).toBe(false);
  });
});
