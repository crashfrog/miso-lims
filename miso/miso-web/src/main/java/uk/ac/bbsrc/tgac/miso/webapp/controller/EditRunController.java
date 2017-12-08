/*
 * Copyright (c) 2012. The Genome Analysis Centre, Norwich, UK
 * MISO project contacts: Robert Davey @ TGAC
 * *********************************************************************
 *
 * This file is part of MISO.
 *
 * MISO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MISO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MISO. If not, see <http://www.gnu.org/licenses/>.
 *
 * *********************************************************************
 */

package uk.ac.bbsrc.tgac.miso.webapp.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

import com.eaglegenomics.simlims.core.User;
import com.eaglegenomics.simlims.core.manager.SecurityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import uk.ac.bbsrc.tgac.miso.core.data.ChangeLog;
import uk.ac.bbsrc.tgac.miso.core.data.Partition;
import uk.ac.bbsrc.tgac.miso.core.data.PartitionQC;
import uk.ac.bbsrc.tgac.miso.core.data.Platform;
import uk.ac.bbsrc.tgac.miso.core.data.Run;
import uk.ac.bbsrc.tgac.miso.core.data.SequencerPartitionContainer;
import uk.ac.bbsrc.tgac.miso.core.data.SequencerReference;
import uk.ac.bbsrc.tgac.miso.core.data.type.HealthType;
import uk.ac.bbsrc.tgac.miso.core.data.type.PlatformType;
import uk.ac.bbsrc.tgac.miso.core.security.util.LimsSecurityUtils;
import uk.ac.bbsrc.tgac.miso.core.util.LimsUtils;
import uk.ac.bbsrc.tgac.miso.core.util.WhineyFunction;
import uk.ac.bbsrc.tgac.miso.dto.Dtos;
import uk.ac.bbsrc.tgac.miso.dto.PartitionDto;
import uk.ac.bbsrc.tgac.miso.service.ChangeLogService;
import uk.ac.bbsrc.tgac.miso.service.PlatformService;
import uk.ac.bbsrc.tgac.miso.service.SequencerReferenceService;
import uk.ac.bbsrc.tgac.miso.service.SequencingParametersService;
import uk.ac.bbsrc.tgac.miso.service.impl.PartitionQCService;
import uk.ac.bbsrc.tgac.miso.service.impl.RunService;
import uk.ac.bbsrc.tgac.miso.webapp.util.JsonArrayCollector;
import uk.ac.bbsrc.tgac.miso.webapp.util.RunMetricsSource;

@Controller
@RequestMapping("/run")
@SessionAttributes("run")
public class EditRunController {
  protected static final Logger log = LoggerFactory.getLogger(EditRunController.class);

  /**
   * Get a stream of source of metrics
   * 
   * Normally, metrics collected by run scanner are stored in the MISO database, but it is possible to provide others here.
   */
  public Stream<RunMetricsSource> getSources() {
    return Stream.of(Run::getMetrics);
  }

  @Autowired
  private SecurityManager securityManager;
  @Autowired
  private ChangeLogService changeLogService;
  @Autowired
  private RunService runService;
  @Autowired
  private PlatformService platformService;
  @Autowired
  private PartitionQCService partitionQCService;

  @Autowired
  private SequencerReferenceService sequencerReferenceService;
  @Autowired
  private SequencingParametersService sequencingParametersService;

  public void setSecurityManager(SecurityManager securityManager) {
    this.securityManager = securityManager;
  }

  public void setRunService(RunService runService) {
    this.runService = runService;
  }

  @ModelAttribute("maxLengths")
  public Map<String, Integer> maxLengths() throws IOException {
    return runService.getRunColumnSizes();
  }

  @ModelAttribute("platformTypes")
  public Collection<String> populatePlatformTypes() throws IOException {
    return PlatformType.platformTypeNames(platformService.listActivePlatformTypes());
  }

  @ModelAttribute("healthTypes")
  public Collection<String> populateHealthTypes() {
    return HealthType.getKeys();
  }

  @ModelAttribute("platforms")
  public Collection<Platform> populatePlatforms() throws IOException {
    return platformService.list();
  }

  public Boolean isMultiplexed(Run run) throws IOException {
    if (run != null && run.getId() != Run.UNSAVED_ID) {
      for (SequencerPartitionContainer f : run.getSequencerPartitionContainers()) {
        for (Partition p : f.getPartitions()) {
          if (p.getPool() != null && p.getPool().getPoolableElementViews().size() > 1) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @RequestMapping(value = "/new/{srId}", method = RequestMethod.GET)
  public ModelAndView newUnassignedRun(@PathVariable Long srId, ModelMap model) throws IOException {
    User user = securityManager.getUserByLoginName(SecurityContextHolder.getContext().getAuthentication().getName());
    // clear any existing run in the model
    model.addAttribute("run", null);
    SequencerReference sequencerReference = sequencerReferenceService.get(srId);
    Run run = sequencerReference.getPlatform().getPlatformType().createRun(user);
    run.setSequencerReference(sequencerReference);
    return setupForm(run, model);

  }

  @RequestMapping(value = "/rest/{runId}", method = RequestMethod.GET)
  public @ResponseBody Run jsonRest(@PathVariable Long runId) throws IOException {
    return runService.get(runId);
  }

  @RequestMapping(value = "/rest/changes", method = RequestMethod.GET)
  public @ResponseBody Collection<ChangeLog> jsonRestChanges() throws IOException {
    return changeLogService.listAll("Run");
  }

  @RequestMapping(value = "/{runId}", method = RequestMethod.GET)
  public ModelAndView setupForm(@PathVariable Long runId, ModelMap model) throws IOException {
    Run run = runService.get(runId);

    return setupForm(run, model);

  }

  @RequestMapping(value = "/alias/{runAlias}", method = RequestMethod.GET)
  public ModelAndView setupForm(@PathVariable String runAlias, ModelMap model) throws IOException {
    Run run = runService.getRunByAlias(runAlias);
    return setupForm(run, model);

  }

  public ModelAndView setupForm(Run run, ModelMap model) throws IOException {

    try {
      User user = securityManager.getUserByLoginName(SecurityContextHolder.getContext().getAuthentication().getName());

      if (run.getId() == Run.UNSAVED_ID) {
        model.put("title", "New Run");
        model.put("multiplexed", false);
        model.put("metrics", "[]");
        model.put("partitionNames", "[]");
      } else {
        model.put("title", "Run " + run.getId());
        model.put("multiplexed", isMultiplexed(run));
        model.put("metrics",
            getSources().filter(Objects::nonNull).map(source -> source.fetchMetrics(run))
                .filter(metrics -> !LimsUtils.isStringBlankOrNull(metrics))
                .collect(new JsonArrayCollector()));
        if (run.getSequencerPartitionContainers().size() == 1) {
          ObjectMapper mapper = new ObjectMapper();
          model.put("partitionNames", mapper.writeValueAsString(
              run.getSequencerPartitionContainers().get(0).getPartitions().stream()
                  .sorted((a, b) -> a.getPartitionNumber() - b.getPartitionNumber())
                  .map(partition -> partition.getPool() == null ? "N/A" : partition.getPool().getAlias()).collect(Collectors.toList())));
        } else {
          model.put("partitionNames", "[]");
        }
      }

      if (!run.userCanRead(user)) {
        throw new SecurityException("Permission denied.");
      }

      model.put("sequencingParameters",
          sequencingParametersService.getForPlatform((long) run.getSequencerReference().getPlatform().getId()));

      model.put("runContainers", run.getSequencerPartitionContainers().stream().map(Dtos::asDto).collect(Collectors.toList()));
      model.put("runPartitions", run.getSequencerPartitionContainers().stream().flatMap(container -> container.getPartitions().stream())
          .map(WhineyFunction.rethrow(partition -> {
            PartitionDto dto = Dtos.asDto(partition);
            PartitionQC qc = partitionQCService.get(run, partition);
            if (qc != null) {
              dto.setQcType(qc.getType().getId());
              dto.setQcNotes(qc.getNotes());
            } else {
              dto.setQcNotes("");
            }
            return dto;
          })).collect(Collectors.toList()));

      model.put("formObj", run);
      model.put("run", run);
      model.put("owners", LimsSecurityUtils.getPotentialOwners(user, run, securityManager.listAllUsers()));
      model.put("accessibleUsers", LimsSecurityUtils.getAccessibleUsers(user, run, securityManager.listAllUsers()));
      model.put("accessibleGroups", LimsSecurityUtils.getAccessibleGroups(user, run, securityManager.listAllGroups()));
      model.put("isWatching", run.getWatchers().contains(user));

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode partitionConfig = mapper.createObjectNode();
      partitionConfig.put("platformType", run.getPlatformType().name());
      partitionConfig.put("platformId", run.getSequencerReference().getPlatform().getId());
      partitionConfig.put("runId", run.getId());
      partitionConfig.put("isFull", run.isFull());
      partitionConfig.put("showContainer", true);
      partitionConfig.put("sequencingParametersId", run.getSequencingParameters() == null ? 0 : run.getSequencingParameters().getId());
      model.put("partitionConfig", mapper.writeValueAsString(partitionConfig));

      return new ModelAndView("/pages/editRun.jsp", model);
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to show run", ex);
      }
      throw ex;
    }
  }

  @RequestMapping(method = RequestMethod.POST)
  public String processSubmit(@ModelAttribute("run") Run run, ModelMap model, SessionStatus session) throws IOException {
    try {
      User user = securityManager.getUserByLoginName(SecurityContextHolder.getContext().getAuthentication().getName());
      for (SequencerPartitionContainer container : run.getSequencerPartitionContainers()) {
        for (Partition partition : container.getPartitions()) {
          if (partition.getPool() != null) {
            partition.getPool().setLastModifier(user);
          }
        }
      }

      long runId = run.getId();
      if (run.getId() == Run.UNSAVED_ID) {
        runId = runService.create(run);
      } else {
        runService.update(run);
      }

      session.setComplete();
      model.clear();
      return "redirect:/miso/run/" + runId;
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Failed to save run", ex);
      }
      throw ex;
    }
  }
}