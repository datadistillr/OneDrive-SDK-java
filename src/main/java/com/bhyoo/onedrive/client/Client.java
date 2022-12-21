package com.bhyoo.onedrive.client;

import com.bhyoo.onedrive.client.auth.AbstractAuthHelper;
import com.bhyoo.onedrive.client.auth.AuthHelper;
import com.bhyoo.onedrive.client.auth.AuthenticationInfo;
import com.bhyoo.onedrive.container.AsyncJobMonitor;
import com.bhyoo.onedrive.container.items.*;
import com.bhyoo.onedrive.container.items.pointer.BasePointer;
import com.bhyoo.onedrive.container.items.pointer.IdPointer;
import com.bhyoo.onedrive.container.items.pointer.Operator;
import com.bhyoo.onedrive.container.items.pointer.PathPointer;
import com.bhyoo.onedrive.container.pager.DriveItemPager;
import com.bhyoo.onedrive.container.pager.DriveItemPager.DriveItemPage;
import com.bhyoo.onedrive.exceptions.ErrorResponseException;
import com.bhyoo.onedrive.exceptions.InternalException;
import com.bhyoo.onedrive.exceptions.InvalidJsonException;
import com.bhyoo.onedrive.network.async.*;
import com.bhyoo.onedrive.network.sync.SyncResponse;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.bhyoo.onedrive.container.items.pointer.Operator.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static java.net.HttpURLConnection.*;

/**
 * @author <a href="mailto:bh322yoo@gmail.com" target="_top">isac322</a>
 */
public class Client {
	public static final String ITEM_ID_PREFIX = "/me/drive/items/";

	/**
	 * Only one {@code mapper} per a {@code Client} object.<br>
	 * It makes possible to multi client usage
	 */
	private @NotNull final RequestTool requestTool;

	@Delegate(types = AbstractAuthHelper.class)
	private @NotNull final AuthHelper authHelper;


	/**
	 * Construct with auto login.
	 *
	 * @param clientId     Client id that MS gave to programmer for identify programmer's applications.
	 * @param scope        Array of scopes that client requires.
	 * @param redirectURL  Redirect URL that programmer already set in Application setting. It must matches with set
	 *                     one!
	 * @param clientSecret Client secret key that MS gave to programmer.
	 *
	 * @throws InternalException             If fail to create {@link URI} object in auth process.
	 *                                       if it happens it's probably bug, so please report to
	 *                                       <a href="mailto:bh322yoo@gmail.com" target="_top">author</a>.
	 * @throws UnsupportedOperationException If the user default browser is not found, or it fails to be launched, or
	 *                                       the default handler application failed to be launched, or the current
	 *                                       platform does not support the {@link java.awt.Desktop.Action#BROWSE}
	 *                                       action.
	 * @throws RuntimeException              if login is unsuccessful.
	 */
	public Client(@NotNull String clientId, @NotNull String[] scope,
				  @NotNull String redirectURL, @NotNull String clientSecret) {
		this(clientId, scope, redirectURL, clientSecret, true);
	}

	/**
	 * @param clientId     Client id that MS gave to programmer for identify programmer's applications.
	 * @param scope        Array of scopes that client requires.
	 * @param redirectURL  Redirect URL that programmer already set in Application setting. It must matches with set
	 *                     one!
	 * @param clientSecret Client secret key that MS gave to programmer.
	 * @param withLogin    if {@code true} construct with login.
	 *
	 * @throws InternalException             If fail to create {@link URI} object in auth process.
	 *                                       if it happens it's probably bug, so please report to
	 *                                       <a href="mailto:bh322yoo@gmail.com" target="_top">author</a>.
	 * @throws UnsupportedOperationException If the user default browser is not found, or it fails to be launched, or
	 *                                       the default handler application failed to be launched, or the current
	 *                                       platform does not support the {@link java.awt.Desktop.Action#BROWSE}
	 *                                       action.
	 * @throws RuntimeException              if login is unsuccessful.
	 */
	public Client(@NotNull String clientId, @NotNull String[] scope, @NotNull String redirectURL,
				  @NotNull String clientSecret, boolean withLogin) {
		requestTool = new RequestTool(this);

		this.authHelper = new AuthHelper(scope, clientId, clientSecret, redirectURL, this.requestTool);

		if (withLogin) login();
	}


	/**
	 * Constructor used when authorization is handled by an external system.  In this case, the accessToken,
	 * refreshToken and expiresIn values have been obtained elsewhere.
	 * @param clientId The OAuth clientID
	 * @param scope A {@link String[]} of scopes
	 * @param redirectURL The OAuth redirect URL
	 * @param clientSecret The OAuth clientSecret token
	 * @param accessToken The OAuth accessToken
	 * @param refreshToken The OAuth refreshToken
	 * @param tokenType The OAuth tokenType
	 * @param expiresIn The OAuth expiresIn value
	 */
	public Client(@NotNull String clientId,
								@NotNull String[] scope,
								@NotNull String redirectURL,
								@NotNull String clientSecret,
								@NotNull String accessToken,
								@NotNull String refreshToken,
								String tokenType,
								long expiresIn) {
		requestTool = new RequestTool(this);
		AuthenticationInfo authInfo = new AuthenticationInfo(tokenType, expiresIn, accessToken, refreshToken, Arrays.toString(scope));
		authHelper = new AuthHelper(scope, clientId, clientSecret, redirectURL, requestTool, authInfo);
	}


	/*
	 *************************************************************
	 *
	 * Regarding drive
	 *
	 *************************************************************
	 */


	@NotNull
	public Drive getDefaultDrive() throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.newRequest("/me/drive").doGet();
		return requestTool.parseDriveAndHandle(response, HTTP_OK);
	}

	@NotNull
	public Drive[] getAllDrive() throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.newRequest("/me/drives").doGet();

		return requestTool.parseDrivePageAndHandle(response, HTTP_OK).getValue();
	}




	/*
	 *************************************************************
	 *
	 * Fetching folder
	 *
	 *************************************************************
	 */


	@NotNull
	public FolderItem getRootDir() throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.newRequest("/me/drive/root/?expand=children").doGet();
		return requestTool.parseFolderItemAndHandle(response, HTTP_OK);
	}


	// TODO: handling error if `id`'s item isn't folder item.

	/**
	 * @param id folder's id.
	 *
	 * @return folder object
	 */
	@NotNull
	public FolderItem getFolder(@NotNull String id) throws ErrorResponseException {
		return getFolder(id, true);
	}

	// TODO: handling error if `id`'s item isn't folder item.
	// FIXME: microsoft's graph issue : expend query doesn't include nextLink
	@NotNull
	public FolderItem getFolder(@NotNull String id, boolean childrenFetching) throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response;

		if (childrenFetching)
			response = requestTool.newRequest(ITEM_ID_PREFIX + id + "?expand=children").doGet();
		else
			response = requestTool.newRequest(ITEM_ID_PREFIX + id).doGet();

		return requestTool.parseFolderItemAndHandle(response, HTTP_OK);
	}

	// TODO: handling error if `pointer`'s item isn't folder item.
	@NotNull
	public FolderItem getFolder(@NotNull BasePointer pointer) throws ErrorResponseException {
		return getFolder(pointer, true);
	}

	// TODO: handling error if `pointer`'s item isn't folder item.
	@NotNull
	public FolderItem getFolder(@NotNull BasePointer pointer, boolean childrenFetching) throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response;

		if (childrenFetching)
			response = requestTool.newRequest(pointer.toASCIIApi() + "?expand=children").doGet();
		else
			response = requestTool.newRequest(pointer.toASCIIApi()).doGet();

		return requestTool.parseFolderItemAndHandle(response, HTTP_OK);
	}




	/*
	 *************************************************************
	 *
	 * Fetching file
	 *
	 *************************************************************
	 */


	/**
	 * @param id file id.
	 *
	 * @return file object
	 */
	@NotNull
	public FileItem getFile(@NotNull String id) throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.newRequest(ITEM_ID_PREFIX + id).doGet();
		return requestTool.parseFileItemAndHandle(response, HTTP_OK);
	}

	@NotNull
	public FileItem getFile(@NotNull BasePointer pointer) throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.newRequest(pointer.toASCIIApi()).doGet();
		return requestTool.parseFileItemAndHandle(response, HTTP_OK);
	}




	/*
	 *************************************************************
	 *
	 * Fetching item
	 *
	 * *************************************************************
	 */


	@NotNull
	public DriveItemFuture getItemAsync(@NotNull String id) {
		authHelper.checkExpired();
		return requestTool.getItemAsync(ITEM_ID_PREFIX + id);
	}

	@NotNull
	public DriveItemFuture getItemAsync(@NotNull BasePointer pointer) {
		authHelper.checkExpired();
		return requestTool.getItemAsync(pointer.toASCIIApi());
	}

	@NotNull
	public DriveItem getItem(@NotNull String id) throws ErrorResponseException {
		authHelper.checkExpired();
		return requestTool.getItem(ITEM_ID_PREFIX + id);
	}

	@NotNull
	public DriveItem getItem(@NotNull BasePointer pointer) throws ErrorResponseException {
		authHelper.checkExpired();
		return requestTool.getItem(pointer.toASCIIApi());
	}

	// FIXME: type conversion
	@NotNull
	public RemoteItem[] getShared() throws ErrorResponseException {
		authHelper.checkExpired();

		ResponseFuture responseFuture = requestTool.doAsync(GET, "/me/drive/sharedWithMe").syncUninterruptibly();
		@NotNull DriveItem[] driveItems = requestTool
				.parseDriveItemRecursiveAndHandle(responseFuture.response(), responseFuture.getNow(), HTTP_OK);

		return (RemoteItem[]) driveItems;
	}




	/*
	 *************************************************************
	 *
	 * Coping OneDrive Item
	 *
	 *************************************************************
	 */


	/**
	 * request to copy {@code srcId} item to new location of {@code destId}.
	 *
	 * @param srcId  item's id that wants to be copied
	 * @param destId location's id that wants to be placed the copied item
	 *
	 * @return monitor {@link AsyncJobMonitor} that can monitor status of copying process
	 *
	 * @throws ErrorResponseException if error happens while requesting copying operation. such as invalid copying
	 *                                request
	 * @throws InvalidJsonException   if fail to parse response of copying request into json. it caused by server side
	 *                                not by SDK.
	 */
	public @NotNull AsyncJobMonitor copyItem(@NotNull String srcId, @NotNull String destId)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"}}").getBytes();
		return copyItem(ITEM_ID_PREFIX + srcId + "/action.copy", content);
	}

	/**
	 * Works just like {@link Client#copyItem(String, String)}} except new name of item can be designated.
	 *
	 * @param newName new name of item that will be copied
	 *
	 * @see Client#copyItem(String, String)
	 */
	public @NotNull AsyncJobMonitor copyItem(@NotNull String srcId, @NotNull String destId, @NotNull String newName)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"},\"name\":\"" + newName + "\"}").getBytes();
		return copyItem(ITEM_ID_PREFIX + srcId + "/" + COPY, content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull String srcId, @NotNull PathPointer destPath)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + destPath.toJson() + "}").getBytes();
		return copyItem(ITEM_ID_PREFIX + srcId + "/" + COPY, content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull String srcId, @NotNull PathPointer dest, @NotNull String newName)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + dest.toJson() + ",\"name\":\"" + newName + "\"}").getBytes();
		return copyItem(ITEM_ID_PREFIX + srcId + "/" + COPY, content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull PathPointer srcPath, @NotNull String destId)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"}}").getBytes();
		return copyItem(srcPath.resolveOperator(COPY), content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull PathPointer srcPath,
											 @NotNull String destId,
											 @NotNull String newName) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"},\"name\":\"" + newName + "\"}").getBytes();
		return copyItem(srcPath.resolveOperator(COPY), content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull BasePointer src, @NotNull BasePointer dest)
			throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + dest.toJson() + "}").getBytes();
		return copyItem(src.resolveOperator(COPY), content);
	}

	public @NotNull AsyncJobMonitor copyItem(@NotNull BasePointer src,
											 @NotNull BasePointer dest,
											 @NotNull String newName) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + dest.toJson() + ",\"name\":\"" + newName + "\"}").getBytes();
		return copyItem(src.resolveOperator(COPY), content);
	}


	// TODO: end of copying process, is this link will be useless or inaccessible ?

	/**
	 * @param api     OneDrive copying api that contains item's location. Note that it must be ensured that
	 *                {@code api} is a escaped {@code String}
	 * @param content HTTP body
	 *
	 * @return monitor {@link AsyncJobMonitor} that can monitor status of copying process
	 *
	 * @throws ErrorResponseException if error happens while requesting copying operation. such as invalid copying
	 *                                request
	 * @throws InvalidJsonException   if fail to parse response of copying request into json. it caused by server side
	 *                                not by SDK.
	 */
	private @NotNull AsyncJobMonitor copyItem(@NotNull String api, @NotNull byte[] content)
			throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.postMetadata(api, content);

		// if not 202 Accepted raise ErrorResponseException
		RequestTool.errorHandling(response, HTTP_ACCEPTED);

		return new AsyncJobMonitor(response.getHeader().get("Location").get(0));
	}




	/*
	 *************************************************************
	 *
	 * Moving OneDrive Item
	 *
	 *************************************************************
	 */

	@NotNull
	public DriveItem moveItem(@NotNull String srcId, @NotNull String destId) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"}}").getBytes();
		return moveItem(ITEM_ID_PREFIX + srcId, content);
	}

	@NotNull
	public DriveItem moveItem(@NotNull String srcId, @NotNull PathPointer destPath) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + destPath.toJson() + "}").getBytes();
		return moveItem(ITEM_ID_PREFIX + srcId, content);
	}

	@NotNull
	public DriveItem moveItem(@NotNull PathPointer srcPath, @NotNull String destId) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":{\"id\":\"" + destId + "\"}}").getBytes();
		return moveItem(srcPath.toASCIIApi(), content);
	}

	@NotNull
	public DriveItem moveItem(@NotNull BasePointer src, @NotNull BasePointer dest) throws ErrorResponseException {
		byte[] content = ("{\"parentReference\":" + dest.toJson() + "}").getBytes();
		return moveItem(src.toASCIIApi(), content);
	}

	@NotNull
	private DriveItem moveItem(@NotNull String api, @NotNull byte[] content) throws ErrorResponseException {
		authHelper.checkExpired();

		// using async way, because some JDK's HttpConnection doesn't allow PATCH
		ResponseFuture future = requestTool.patchMetadataAsync(api, content).syncUninterruptibly();

		return requestTool.parseDriveItemAndHandle(future.response(), future.getNow(), HTTP_OK);
	}




	/*
	 *************************************************************
	 *
	 * Creating folder
	 *
	 *************************************************************
	 */


	// TODO: Implement '@name.conflictBehavior'

	/**
	 * Implementation of <a href='https://dev.onedrive.com/items/create.htm'>detail</a>.
	 * <p>
	 *
	 * @param parentId Parent ID that creating folder inside.
	 * @param name     New folder name.
	 *
	 * @return New folder's object.
	 *
	 * @throws RuntimeException If creating folder or converting response is fails.
	 */
	@NotNull
	public FolderItem createFolder(@NotNull String parentId, @NotNull String name) throws ErrorResponseException {
		byte[] content = ("{\"name\":\"" + name + "\",\"folder\":{}}").getBytes();
		return createFolder(ITEM_ID_PREFIX + parentId + "/children", content);
	}

	// TODO: Implement '@name.conflictBehavior'

	/**
	 * Implementation of <a href='https://dev.onedrive.com/items/create.htm'>detail</a>.
	 * <p>
	 *
	 * @param parent Parent pointer that creating folder inside. (either ID or path)
	 * @param name   New folder name.
	 *
	 * @return New folder's object.
	 *
	 * @throws RuntimeException If creating folder or converting response is fails.
	 */
	@NotNull
	public FolderItem createFolder(@NotNull BasePointer parent, @NotNull String name) throws ErrorResponseException {
		byte[] content = ("{\"name\":\"" + name + "\",\"folder\":{}}").getBytes();
		return createFolder(parent.resolveOperator(CHILDREN), content);
	}

	@NotNull
	private FolderItem createFolder(@NotNull String api, @NotNull byte[] content) throws ErrorResponseException {
		authHelper.checkExpired();

		SyncResponse response = requestTool.postMetadata(api, content);
		return requestTool.parseFolderItemAndHandle(response, HTTP_CREATED);
	}




	/*
	 *************************************************************
	 *
	 * Downloading files
	 *
	 *************************************************************
	 */


	public void download(@NotNull String fileId, @NotNull Path downloadFolder) throws IOException {
		_downloadAsync(Client.ITEM_ID_PREFIX + fileId, downloadFolder, null).syncUninterruptibly();
	}

	public void download(@NotNull String fileId, @NotNull Path downloadFolder,
						 @NotNull String newName) throws IOException {
		_downloadAsync(Client.ITEM_ID_PREFIX + fileId + "/content", downloadFolder, newName).syncUninterruptibly();
	}

	public void download(@NotNull BasePointer file, @NotNull Path downloadFolder) throws IOException {
		_downloadAsync(file.toASCIIApi(), downloadFolder, null).syncUninterruptibly();
	}

	public void download(@NotNull BasePointer file, @NotNull Path downloadFolder,
						 @NotNull String newName) throws IOException {
		_downloadAsync(file.resolveOperator(Operator.CONTENT), downloadFolder, newName).syncUninterruptibly();
	}


	public DownloadFuture downloadAsync(@NotNull String fileId, @NotNull Path downloadFolder) throws IOException {
		return _downloadAsync(Client.ITEM_ID_PREFIX + fileId + "/content", downloadFolder, null);
	}


	public DownloadFuture downloadAsync(@NotNull String fileId, @NotNull Path downloadFolder,
										@Nullable String newName) throws IOException {
		return _downloadAsync(Client.ITEM_ID_PREFIX + fileId + "/content", downloadFolder, newName);
	}

	public DownloadFuture downloadAsync(@NotNull BasePointer pointer,
										@NotNull Path downloadFolder) throws IOException {
		return _downloadAsync(pointer.resolveOperator(Operator.CONTENT), downloadFolder, null);
	}

	public DownloadFuture downloadAsync(@NotNull BasePointer pointer, @NotNull Path downloadFolder,
										@Nullable String newName) throws IOException {
		return _downloadAsync(pointer.resolveOperator(Operator.CONTENT), downloadFolder, newName);
	}

	private DownloadFuture _downloadAsync(@NotNull String api, @NotNull Path downloadFolder,
										  @Nullable String newName) throws IOException {
		downloadFolder = downloadFolder.toAbsolutePath().normalize();

		// it's illegal if and only if `downloadFolder` exists but not directory.
		if (Files.exists(downloadFolder) && !Files.isDirectory(downloadFolder))
			throw new IllegalArgumentException(downloadFolder + " already exists and isn't folder.");

		Files.createDirectories(downloadFolder);

		return new AsyncDownloadClient(getFullToken(), RequestTool.api2Uri(api), downloadFolder, newName).execute();
	}




	/*
	 *************************************************************
	 *
	 * Uploading files
	 *
	 *************************************************************
	 */


	public UploadFuture uploadFile(@NotNull String parentId, @NotNull Path filePath) {
		String fileName = filePath.getFileName().toString();
		return requestTool.upload(
				ITEM_ID_PREFIX + parentId + ":/" + fileName + ":/" + CREATE_UPLOAD_SESSION, filePath);
	}

	public UploadFuture uploadFile(@NotNull IdPointer parentId, @NotNull Path filePath) {
		String fileName = filePath.getFileName().toString();
		return requestTool.upload(
				parentId.toASCIIApi() + ":/" + fileName + ":/" + CREATE_UPLOAD_SESSION, filePath);
	}

	public UploadFuture uploadFile(@NotNull PathPointer parentPath, @NotNull Path filePath) {
		String fileName = filePath.getFileName().toString();
		return requestTool.upload(parentPath.resolve(fileName).resolveOperator(CREATE_UPLOAD_SESSION), filePath);
	}


	/**
	 * Upload file {@code filePath} to {@code parentId}. This method only supports files up to 4MB in size.
	 *
	 * @param parentId The ID of the directory where the file to be uploaded is stored.
	 * @param filePath local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 *
	 * @throws IOException            when can not read from {@code filePath}
	 * @throws ErrorResponseException somethings wrong with response of MS
	 */
	public @NotNull FileItem simpleUploadFile(@NotNull String parentId, @NotNull Path filePath)
			throws IOException, ErrorResponseException {
		String fileName = filePath.getFileName().toString();
		return requestTool.simpleUpload(ITEM_ID_PREFIX + parentId + ":/" + fileName + ":/" + CONTENT, filePath);
	}

	/**
	 * Upload file {@code filePath} to {@code parentId}. This method only supports files up to 4MB in size.
	 *
	 * @param parentId The ID of the directory where the file to be uploaded is stored.
	 * @param filePath local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 *
	 * @throws IOException            when can not read from {@code filePath}
	 * @throws ErrorResponseException somethings wrong with response of MS
	 */
	public FileItem simpleUploadFile(@NotNull IdPointer parentId, @NotNull Path filePath)
			throws IOException, ErrorResponseException {
		String fileName = filePath.getFileName().toString();
		return requestTool.simpleUpload(parentId.toASCIIApi() + ":/" + fileName + ":/" + CONTENT, filePath);
	}

	/**
	 * Upload file {@code filePath} to {@code parentPath}. This method only supports files up to 4MB in size.
	 *
	 * @param parentPath The path of the directory where the file to be uploaded is stored.
	 * @param filePath   local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 *
	 * @throws IOException            when can not read from {@code filePath}
	 * @throws ErrorResponseException somethings wrong with response of MS
	 */
	public FileItem simpleUploadFile(@NotNull PathPointer parentPath, @NotNull Path filePath)
			throws IOException, ErrorResponseException {
		String fileName = filePath.getFileName().toString();
		return requestTool.simpleUpload(parentPath.resolve(fileName).resolveOperator(CONTENT), filePath);
	}


	/**
	 * Upload file {@code filePath} to {@code parentId}. This method only supports files up to 4MB in size.
	 *
	 * @param parentId The ID of the directory where the file to be uploaded is stored.
	 * @param filePath local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 */
	public DefaultDriveItemPromise simpleUploadFileAsync(@NotNull String parentId, @NotNull Path filePath) {
		String fileName = Paths.get(filePath.getFileName().toUri().toASCIIString()).getFileName().toString();
		return requestTool.simpleUploadAsync(ITEM_ID_PREFIX + parentId + ":/" + fileName + ":/" + CONTENT, filePath);
	}

	/**
	 * Upload file {@code filePath} to {@code parentId}. This method only supports files up to 4MB in size.
	 *
	 * @param parentId The ID of the directory where the file to be uploaded is stored.
	 * @param filePath local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 */
	public DefaultDriveItemPromise simpleUploadFileAsync(@NotNull IdPointer parentId, @NotNull Path filePath) {
		String fileName = Paths.get(filePath.getFileName().toUri().toASCIIString()).getFileName().toString();
		return requestTool.simpleUploadAsync(parentId.toASCIIApi() + ":/" + fileName + ":/" + CONTENT, filePath);
	}

	/**
	 * Upload file {@code filePath} to {@code parentPath}. This method only supports files up to 4MB in size.
	 *
	 * @param parentPath The path of the directory where the file to be uploaded is stored.
	 * @param filePath   local file path to upload
	 *
	 * @return {@link FileItem} object of created item
	 */
	public DefaultDriveItemPromise simpleUploadFileAsync(@NotNull PathPointer parentPath, @NotNull Path filePath) {
		String fileName = Paths.get(filePath.getFileName().toUri().toASCIIString()).getFileName().toString();
		return requestTool.simpleUploadAsync(parentPath.resolve(fileName).resolveOperator(CONTENT), filePath);
	}



	/*
	 *************************************************************
	 *
	 * Deleting item
	 *
	 *************************************************************
	 */


	public void deleteItem(@NotNull String id) throws ErrorResponseException {
		SyncResponse response = requestTool.newRequest(Client.ITEM_ID_PREFIX + id).doDelete();

		// if response isn't 204 No Content
		RequestTool.errorHandling(response, HTTP_NO_CONTENT);
	}

	public void deleteItem(@NotNull BasePointer pointer) throws ErrorResponseException {
		SyncResponse response = requestTool.newRequest(pointer.toASCIIApi()).doDelete();

		// if response isn't 204 No Content
		RequestTool.errorHandling(response, HTTP_NO_CONTENT);
	}




	/*
	 *************************************************************
	 *
	 * Searching files
	 *
	 *************************************************************
	 */

	public @NotNull DriveItemPager searchItem(String query) throws ErrorResponseException, IOException {
		String rawQuery = URLEncoder.encode(query, "UTF-8");
		SyncResponse response = requestTool.newRequest("/me/drive/root/search(q='" + rawQuery + "')").doGet();

		return requestTool.parseDriveItemPagerAndHandle(response, HTTP_OK);
	}

	public @NotNull DriveItemPage searchItem(String query, String driveId) throws ErrorResponseException,
			IOException {
		String rawQuery = URLEncoder.encode(query, "UTF-8");
		SyncResponse response =
				requestTool.newRequest("/drives/" + driveId + "/root/search(q='" + rawQuery + "')").doGet();

		return requestTool.parseDriveItemPageAndHandle(response, HTTP_OK);
	}

	public @NotNull DriveItemPage searchItem(String query, Drive drive) throws ErrorResponseException,
			IOException {
		String rawQuery = URLEncoder.encode(query, "UTF-8");
		SyncResponse response =
				requestTool.newRequest("/drives/" + drive.getId() + "/root/search(q='" + rawQuery + "')").doGet();

		return requestTool.parseDriveItemPageAndHandle(response, HTTP_OK);
	}





	/*
	 *************************************************************
	 *
	 * Custom Getter
	 *
	 *************************************************************
	 */

	public @NotNull RequestTool requestTool() {return requestTool;}
}
