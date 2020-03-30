package org.apache.unomi.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.*;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.AccountService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A JAX-RS endpoint to manage {@link Profile}s and {@link Persona}s.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
				allowAllOrigins = true,
				allowCredentials = true
)
public class AccountServiceEndPoint {
	private static final Logger logger = LoggerFactory.getLogger(AccountServiceEndPoint.class.getName());

	private AccountService accountService;

	private EventService eventService;

	public AccountServiceEndPoint() {
		logger.info("Initializing account service endpoint...");
	}

	@WebMethod(exclude = true)
	public void setAccountService(AccountService accountService) {
		this.accountService = accountService;
	}

	@WebMethod(exclude = true)
	public void setEventService(EventService eventService) {
		this.eventService = eventService;
	}

	@GET
	@Path("/count")
	public long getAllAccountsCount() {
		return accountService.getAllAccountsCount();
	}

	@GET
	@Path("/search")
	public PartialList<Account> getAccounts(Query query) {
		return accountService.search(query, Account.class);
	}

	@POST
	@Path("/export")
	@Produces("text/csv")
	public Response exportAccounts(Query query) {
		String toCsv = accountService.exportAccountsPropertiesToCsv(query);
		Response.ResponseBuilder response = Response.ok(toCsv);
		response.header("Content-Disposition", "attachment; filename=Accounts_export_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date() + ".csv"));
		return response.build();
	}

	@GET
	@Path("/export")
	@Produces("text/csv")
	public Response getExportAccounts(@QueryParam("query") String query) {
		try {
			return exportAccounts(CustomObjectMapper.getObjectMapper().readValue(query, Query.class));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return Response.serverError().build();
		}
	}

	@GET
	@Path("/export")
	@Produces("text/csv")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response formExportAccounts(@FormParam("query") String query) {
		try {
			return exportAccounts(CustomObjectMapper.getObjectMapper().readValue(query, Query.class));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return Response.serverError().build();
		}
	}

	@POST
	@Path("/batchAccountsUpdate")
	public void batchAccountsUpdate(BatchUpdate update) {
		accountService.batchAccountsUpdate(update);
	}

	@GET
	@Path("/{accountId}")
	public Account load(@PathParam("accountId") String accountId) {
		return accountService.load(accountId);
	}

	@POST
	@Path("/")
	public Account save(Account account) {
		Account savedAccount = accountService.saveOrMerge(account);
		if (savedAccount != null) {
			Event accountUpdated  = new Event("accountUpdated", null, savedAccount, null, null, savedAccount, new Date());
			accountUpdated.setPersistent(false);
			int changes = eventService.send(accountUpdated);
			if ((changes & EventService.ACCOUNT_UDPATED) == EventService.ACCOUNT_UDPATED) {
				accountService.save(savedAccount);
			}
		}
		return savedAccount;
	}

	@DELETE
	@Path("/{accountId")
	public void delete(@PathParam("accountId") String accountId, @QueryParam("profileId") @DefaultValue("") String profileId) {
		if (!profileId.equals("")) {
			accountService.delete(accountId, profileId);
		} else {
			accountService.delete(accountId);
		}
	}

	// TODO: add endpoints to retrive properties if necessary


}
