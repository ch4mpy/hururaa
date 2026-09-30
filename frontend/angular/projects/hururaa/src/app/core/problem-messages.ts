import { HttpErrorResponse } from '@angular/common/http';
import { HururaaProblemDetail, ProblemType, ValidationProblemDetail } from '@api/hururaa-api';

/** The named values a problem carries, as documented on the backend's `ProblemType` constants. */
type Parameters = HururaaProblemDetail['parameters'];

const str = (value: unknown): string =>
  typeof value === 'string' || typeof value === 'number' ? String(value) : '';

/**
 * One localized message per problem `type` the API can report (see `ProblemType`, generated from
 * the backend's enum). Typed as an exhaustive record: a type added on the backend fails compilation
 * here after `npm run api` until it gets its message.
 */
const PROBLEM_MESSAGES: Record<ProblemType, (p: Parameters) => string> = {
  [ProblemType.VALIDATION]: () => $localize`:@@problem.validation:Certaines valeurs sont invalides`,
  [ProblemType.DIRECTION_NOT_FOUND]: (p) =>
    $localize`:@@problem.directionNotFound:Direction ${str(p['direction'])}:direction: inconnue`,
  [ProblemType.GROUP_NOT_FOUND]: (p) =>
    $localize`:@@problem.groupNotFound:Groupe ${str(p['group'])}:group: inconnu dans la direction ${str(p['direction'])}:direction:`,
  [ProblemType.NOT_A_MEMBER]: (p) =>
    $localize`:@@problem.notAMember:L'utilisateur ${str(p['userId'])}:userId: n'est pas membre de la direction`,
  [ProblemType.APPLICATION_NOT_FOUND]: (p) =>
    $localize`:@@problem.applicationNotFound:Application n° ${str(p['applicationId'])}:applicationId: introuvable`,
  [ProblemType.APPLICATION_ALREADY_EXISTS]: (p) =>
    $localize`:@@problem.applicationAlreadyExists:Une application utilise déjà le préfixe ${str(p['clientPrefix'])}:clientPrefix:`,
  [ProblemType.APPLICATION_ROLE_NOT_FOUND]: (p) =>
    $localize`:@@problem.applicationRoleNotFound:Le client ${str(p['clientId'])}:clientId: n'a pas de rôle ${str(p['role'])}:role:`,
  [ProblemType.APPLICATION_NOT_IN_DIRECTION]: (p) =>
    $localize`:@@problem.applicationNotInDirection:L'application n° ${str(p['applicationId'])}:applicationId: n'est pas gérée par la direction ${str(p['direction'])}:direction: : ses rôles ne peuvent pas être attribués par les groupes de cette direction`,
  [ProblemType.APPLICATION_ROLES_STILL_GRANTED]: (p) =>
    $localize`:@@problem.applicationRolesStillGranted:Des groupes attribuent encore des rôles de l'application (${str(p['groups'])}:groups:) : retirez-les d'abord`,
  [ProblemType.CONCURRENT_MODIFICATION]: () =>
    $localize`:@@problem.concurrentModification:Modifié entre-temps par quelqu'un d'autre : rechargez la page avant de réessayer`,
  [ProblemType.DATA_INTEGRITY_VIOLATION]: () =>
    $localize`:@@problem.dataIntegrityViolation:La demande est en conflit avec des données existantes`,
  [ProblemType.IDENTITY_PROVIDER_ERROR]: () =>
    $localize`:@@problem.identityProviderError:La communication avec le serveur d'identité a échoué : réessayez plus tard`,
};

const PROBLEM_TYPES = new Set<string>(Object.values(ProblemType));

/** Whether an HTTP error body is one of the API's typed problems (`application/problem+json`). */
export function isHururaaProblem(body: unknown): body is HururaaProblemDetail {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as HururaaProblemDetail).type === 'string' &&
    PROBLEM_TYPES.has((body as HururaaProblemDetail).type)
  );
}

export function isValidationProblem(body: unknown): body is ValidationProblemDetail {
  return isHururaaProblem(body) && body.type === ProblemType.VALIDATION;
}

/**
 * The localized message for a failed API call, or `undefined` when the error is not one of the
 * API's typed problems (network failure, gateway `401`/`403`, unknown problem...): callers keep
 * their own generic "failed" message for those.
 */
export function problemMessage(error: unknown): string | undefined {
  const body: unknown = error instanceof HttpErrorResponse ? error.error : error;
  return isHururaaProblem(body) ? PROBLEM_MESSAGES[body.type](body.parameters) : undefined;
}
