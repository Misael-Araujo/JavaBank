# Business behavior changes

- **Opening balance:** `POST /auth/register` always creates a zero-balance account. The old client-supplied `wallet` field was removed so users cannot create funds at registration.
- **Funding for demos/operations:** added ADMIN-only `POST /admin/users/{id}/credits`. The administrator is bootstrapped only when both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are configured. Every credit is recorded as an `ADMIN_CREDIT` history entry.
- **Transfer identity:** transfer requests now contain only `recipientId` and `amount`; the sender is derived from the validated JWT rather than a client-supplied payer ID. Only COMMON accounts can send, the minimum remains 1.00, and transfers to the same account are rejected.
- **External authorization:** the authorization integration remains mandatory and fails closed on declined, malformed, or unavailable responses. Its URL is configurable and requests are bounded by connect/read timeouts.
- **Notifications:** notifications now run after the transfer transaction commits. A rejected or unavailable notification is logged without reversing a completed transfer, preventing clients from seeing a failed response after funds have already moved.
- **Legacy migration limits:** V1–V4 did not persist transfer amounts or user passwords. V5 copies amounts from `transferred_value` when that legacy JPA column exists; records without a stored value remain null. Old accounts receive a non-authenticating password marker and require a separate verified recovery process.
