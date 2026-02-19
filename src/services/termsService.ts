import { Preferences } from '@capacitor/preferences';

const TERMS_ACCEPTED_KEY = 'terms_accepted';

export const termsService = {
    async hasAccepted(): Promise<boolean> {
        try {
            const { value } = await Preferences.get({ key: TERMS_ACCEPTED_KEY });
            return value === 'true';
        } catch {
            return false;
        }
    },

    async accept(): Promise<void> {
        await Preferences.set({ key: TERMS_ACCEPTED_KEY, value: 'true' });
    },

    async reset(): Promise<void> {
        await Preferences.remove({ key: TERMS_ACCEPTED_KEY });
    },
};
