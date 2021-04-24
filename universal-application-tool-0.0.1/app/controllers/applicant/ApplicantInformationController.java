package controllers.applicant;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import auth.ProfileUtils;
import controllers.CiviFormController;
import forms.ApplicantInformationForm;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import models.Applicant;
import play.data.Form;
import play.data.FormFactory;
import play.i18n.MessagesApi;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import repository.ApplicantRepository;
import services.applicant.ApplicantData;
import services.applicant.ApplicantNotFoundException;
import views.applicant.ApplicantInformationView;

/**
 * Provides methods for editing and updating an applicant's information, such as their preferred
 * language.
 */
public final class ApplicantInformationController extends CiviFormController {

  private final HttpExecutionContext httpExecutionContext;
  private final MessagesApi messagesApi;
  private final ApplicantInformationView informationView;
  private final ApplicantRepository repository;
  private final FormFactory formFactory;
  private final ProfileUtils profileUtils;

  @Inject
  public ApplicantInformationController(
      HttpExecutionContext httpExecutionContext,
      MessagesApi messagesApi,
      ApplicantInformationView informationView,
      ApplicantRepository repository,
      FormFactory formFactory,
      ProfileUtils profileUtils) {
    this.httpExecutionContext = httpExecutionContext;
    this.messagesApi = messagesApi;
    this.informationView = informationView;
    this.repository = repository;
    this.formFactory = formFactory;
    this.profileUtils = profileUtils;
  }

  public CompletionStage<Result> edit(Http.Request request, long applicantId) {
    return checkApplicantAuthorization(profileUtils, request, applicantId)
        .thenApplyAsync(
            v -> ok(informationView.render(request, applicantId)), httpExecutionContext.current())
        .exceptionally(
            ex -> {
              if (ex instanceof CompletionException) {
                if (ex.getCause() instanceof SecurityException) {
                  return unauthorized();
                }
              }
              throw new RuntimeException(ex);
            });
  }

  public CompletionStage<Result> update(Http.Request request, long applicantId) {
    Form<ApplicantInformationForm> form = formFactory.form(ApplicantInformationForm.class);
    if (form.hasErrors()) {
      return supplyAsync(Results::badRequest);
    }
    ApplicantInformationForm infoForm = form.bindFromRequest(request).get();

    return checkApplicantAuthorization(profileUtils, request, applicantId)
        .thenComposeAsync(
            v -> repository.lookupApplicant(applicantId), httpExecutionContext.current())
        .thenComposeAsync(
            maybeApplicant -> {
              // Set preferred locale.
              if (maybeApplicant.isPresent()) {
                Applicant applicant = maybeApplicant.get();
                ApplicantData data = applicant.getApplicantData();
                data.setPreferredLocale(infoForm.getLocale());
                // Update the applicant, then pass the updated applicant to the next stage.
                return repository
                    .updateApplicant(applicant)
                    .thenApplyAsync(v -> applicant, httpExecutionContext.current());
              } else {
                return CompletableFuture.failedFuture(new ApplicantNotFoundException(applicantId));
              }
            },
            httpExecutionContext.current())
        .thenApplyAsync(
            applicant -> {
              Locale preferredLocale = applicant.getApplicantData().preferredLocale();
              return redirect(routes.ApplicantProgramsController.index(applicantId))
                  .withLang(preferredLocale, messagesApi);
            },
            httpExecutionContext.current())
        .exceptionally(
            ex -> {
              if (ex instanceof CompletionException) {
                if (ex.getCause() instanceof SecurityException) {
                  return unauthorized();
                }
                if (ex.getCause() instanceof ApplicantNotFoundException) {
                  return badRequest(ex.getCause().getMessage());
                }
              }
              throw new RuntimeException(ex);
            });
  }
}