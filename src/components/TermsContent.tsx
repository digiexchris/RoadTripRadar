import React from 'react';
import './TermsContent.css';

const TermsContent: React.FC = () => (
    <div className="terms-content">
        <h2>Terms &amp; Conditions</h2>
        <p className="terms-effective">Effective Date: February 2026</p>

        <h3>Important Disclaimer</h3>
        <p>
            The weather radar imagery, location data, heading information, and all
            other data displayed by this application may be <strong>inaccurate,
            incomplete, delayed, or otherwise incorrect</strong>. This application
            is intended for informational and entertainment purposes only.
        </p>
        <p>
            <strong>You must always verify weather conditions, road safety, and any
            other information with trusted, authoritative sources</strong> (such as
            the National Weather Service, local meteorological agencies, or official
            aviation weather services) before making any decisions that could affect
            your safety, travel plans, or property.
        </p>
        <p>
            Do not rely on this application as your sole source of weather or
            navigation information. Failure to consult official sources may result
            in personal injury, property damage, or other harm.
        </p>

        <h3>No Warranty</h3>
        <p>
            This software is provided <strong>"AS IS" and "AS AVAILABLE"</strong>,
            without warranty of any kind, express or implied, including but not
            limited to the warranties of merchantability, fitness for a particular
            purpose, accuracy, and non-infringement. The authors and contributors
            make no warranty that the application will be uninterrupted, timely,
            secure, or error-free.
        </p>

        <h3>Limitation of Liability</h3>
        <p>
            In no event shall the authors, contributors, copyright holders, or
            distributors of this software be liable for any claim, damages, or
            other liability, whether in an action of contract, tort, or otherwise,
            arising from, out of, or in connection with the software or the use or
            other dealings in the software. This includes, without limitation, any
            direct, indirect, incidental, special, consequential, or punitive
            damages, including but not limited to loss of profits, data, use,
            goodwill, or other intangible losses.
        </p>

        <h3>Third-Party Data</h3>
        <p>
            This application relies on third-party services and APIs (including but
            not limited to RainViewer, OpenStreetMap, and device sensor data) for
            its functionality. The authors have no control over and assume no
            responsibility for the content, accuracy, privacy policies, or
            practices of any third-party services.
        </p>

        <h3>Assumption of Risk</h3>
        <p>
            By using this application, you acknowledge and agree that you assume all
            risk associated with your use of the information provided. You agree to
            hold harmless the authors and contributors from any and all claims,
            losses, liabilities, damages, costs, and expenses arising from your use
            of this application.
        </p>

        <h3>Acceptance</h3>
        <p>
            By tapping "I Agree" or continuing to use this application, you
            acknowledge that you have read, understood, and agree to be bound by
            these terms and conditions.
        </p>
    </div>
);

export default TermsContent;
