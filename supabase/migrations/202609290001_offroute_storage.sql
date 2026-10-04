begin;

-- Existing recordings are preserved. The app stores chunks below the 50 MB limit.
insert into storage.buckets (id, name, public, file_size_limit)
values ('journeys', 'journeys', false, 52428800),
       ('model-releases', 'model-releases', false, 52428800)
on conflict (id) do update set public = false;

drop policy if exists "offroute_trip_read" on storage.objects;
create policy "offroute_trip_read" on storage.objects for select to authenticated
using (bucket_id = 'journeys' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists "offroute_trip_insert" on storage.objects;
create policy "offroute_trip_insert" on storage.objects for insert to authenticated
with check (bucket_id = 'journeys' and (storage.foldername(name))[1] = (select auth.uid())::text);
drop policy if exists "offroute_trip_update" on storage.objects;
create policy "offroute_trip_update" on storage.objects for update to authenticated
using (bucket_id = 'journeys' and (storage.foldername(name))[1] = (select auth.uid())::text)
with check (bucket_id = 'journeys' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Only administrators publish approved releases in this bucket.
drop policy if exists "offroute_model_read" on storage.objects;
create policy "offroute_model_read" on storage.objects for select to authenticated
using (bucket_id = 'model-releases');

commit;
