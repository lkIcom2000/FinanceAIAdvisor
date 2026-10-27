package de.financetool.monitoring.application;

import de.financetool.monitoring.adapter.NotificationRepository;
import de.financetool.monitoring.adapter.TriggerRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persona reset of the monitoring module: the fixtures hold no Triggers, so they and their Notifications go. */
@Service
class MonitoringResetter implements PersonaResettable {

    private final TriggerRepository triggers;
    private final NotificationRepository notifications;

    MonitoringResetter(TriggerRepository triggers, NotificationRepository notifications) {
        this.triggers = triggers;
        this.notifications = notifications;
    }

    @Override
    public String module() {
        return "monitoring";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        notifications.deletePersonaRows(personaId);
        triggers.deletePersonaRows(personaId);
    }
}
