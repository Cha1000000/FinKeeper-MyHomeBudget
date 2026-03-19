import { describe, it, expect, beforeEach } from 'vitest';
import {
    shouldShowSecurityOnboardingPrompt,
    dismissSecurityOnboardingPrompt,
    resetSecurityOnboardingPromptState,
} from '../onboarding/securityPrompt';

describe('securityPrompt', () => {
    beforeEach(() => {
        resetSecurityOnboardingPromptState();
    });

    it('should not show prompt if user recoverability status is protected', () => {
        const user = {
            recoverabilityStatus: 'protected',
        } as any;

        expect(shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus)).toBe(false);
    });

    it('should show prompt for unprotected user on first visit', () => {
        const user = {
            recoverabilityStatus: 'unprotected',
        } as any;

        const result = shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus);
        expect(result).toBe(true);
    });

    it('should not show prompt again after dismiss in same session', () => {
        const user = {
            recoverabilityStatus: 'unprotected',
        } as any;

        dismissSecurityOnboardingPrompt(user.recoverabilityStatus);
        expect(shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus)).toBe(false);
    });


});
