import { describe, it, expect, beforeEach } from 'vitest';
import {
    shouldShowSecurityOnboardingPrompt,
    dismissSecurityOnboardingPrompt,
    resetSecurityOnboardingPromptState,
} from '../onboarding/securityPrompt';
import type { User } from '../api';

describe('securityPrompt', () => {
    beforeEach(() => {
        resetSecurityOnboardingPromptState();
    });

    it('should not show prompt if user recoverability status is protected', () => {
        const user: Pick<User, 'recoverabilityStatus'> = {
            recoverabilityStatus: 'protected',
        };

        expect(shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus)).toBe(false);
    });

    it('should show prompt for unprotected user on first visit', () => {
        const user: Pick<User, 'recoverabilityStatus'> = {
            recoverabilityStatus: 'unprotected',
        };

        const result = shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus);
        expect(result).toBe(true);
    });

    it('should not show prompt again after dismiss in same session', () => {
        const user: Pick<User, 'recoverabilityStatus'> = {
            recoverabilityStatus: 'unprotected',
        };

        dismissSecurityOnboardingPrompt(user.recoverabilityStatus);
        expect(shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus)).toBe(false);
    });
});
