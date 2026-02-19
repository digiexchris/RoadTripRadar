import { Redirect, Route } from 'react-router-dom';
import { IonApp, IonRouterOutlet, IonModal, IonContent, IonButton, IonPage, setupIonicReact } from '@ionic/react';
import { IonReactRouter } from '@ionic/react-router';
import { useState, useEffect } from 'react';
import Home from './pages/Home';
import Settings from './pages/Settings';
import { SettingsProvider } from './contexts/SettingsContext';
import TermsContent from './components/TermsContent';
import { termsService } from './services/termsService';

/* Core CSS required for Ionic components to work properly */
import '@ionic/react/css/core.css';

/* Basic CSS for apps built with Ionic */
import '@ionic/react/css/normalize.css';
import '@ionic/react/css/structure.css';
import '@ionic/react/css/typography.css';

/* Optional CSS utils that can be commented out */
import '@ionic/react/css/padding.css';
import '@ionic/react/css/float-elements.css';
import '@ionic/react/css/text-alignment.css';
import '@ionic/react/css/text-transformation.css';
import '@ionic/react/css/flex-utils.css';
import '@ionic/react/css/display.css';

/**
 * Ionic Dark Mode
 * -----------------------------------------------------
 * For more info, please see:
 * https://ionicframework.com/docs/theming/dark-mode
 */

/* import '@ionic/react/css/palettes/dark.always.css'; */
/* import '@ionic/react/css/palettes/dark.class.css'; */
import '@ionic/react/css/palettes/dark.system.css';

/* Theme variables */
import './theme/variables.css';

setupIonicReact();

const App: React.FC = () => {
  const [termsAccepted, setTermsAccepted] = useState<boolean | null>(null);

  useEffect(() => {
    termsService.hasAccepted().then(setTermsAccepted);
  }, []);

  const handleAccept = async () => {
    await termsService.accept();
    setTermsAccepted(true);
  };

  // Still loading acceptance status
  if (termsAccepted === null) {
    return <IonApp />;
  }

  return (
    <IonApp>
      {/* Terms gate — blocks all content until accepted */}
      <IonModal isOpen={!termsAccepted} backdropDismiss={false}>
        <IonPage>
          <IonContent className="ion-padding">
            <TermsContent />
            <div style={{ padding: '0 1rem 2rem' }}>
              <IonButton expand="block" onClick={handleAccept}>
                I Agree
              </IonButton>
            </div>
          </IonContent>
        </IonPage>
      </IonModal>

      <SettingsProvider>
        <IonReactRouter>
          <IonRouterOutlet>
            <Route exact path="/home">
              <Home />
            </Route>
            <Route exact path="/settings">
              <Settings />
            </Route>
            <Route exact path="/">
              <Redirect to="/home" />
            </Route>
          </IonRouterOutlet>
        </IonReactRouter>
      </SettingsProvider>
    </IonApp>
  );
};

export default App;
