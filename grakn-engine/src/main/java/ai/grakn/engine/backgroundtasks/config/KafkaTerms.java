/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.engine.backgroundtasks.config;

/**
 * <p>
 * Class containing strings that describe the Kafka queues and groups
 * </p>
 *
 * @author Denis Lobanov, alexandraorth
 */
public interface KafkaTerms {
    String TASK_RUNNER_GROUP = "task-runners";
    String SCHEDULERS_GROUP = "schedulers";
    String WORK_QUEUE_TOPIC = "work-queue";
    String NEW_TASKS_TOPIC = "new-tasks";
    String LOG_TOPIC = "logs";
}
