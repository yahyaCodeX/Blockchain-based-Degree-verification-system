package com.example.contract;

import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple8;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DegreeVault extends Contract {
    public static final String BINARY = "";

    private static final String FUNC_ISSUEDEGREE = "issueDegree";
    private static final String FUNC_VERIFYDEGREE = "verifyDegree";
    private static final String FUNC_ISSUEBATCH = "issueBatch";
    private static final String FUNC_GETDEGREECOUNT = "getDegreeCount";
    private static final String FUNC_GETBATCHSIZE = "getBatchSize";
    private static final String FUNC_UNIVERSITYADMIN = "universityAdmin";

    protected DegreeVault(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, credentials, gasProvider);
    }

    protected DegreeVault(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider gasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, gasProvider);
    }

    public static DegreeVault load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider gasProvider) {
        return new DegreeVault(contractAddress, web3j, credentials, gasProvider);
    }

    public static DegreeVault load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider gasProvider) {
        return new DegreeVault(contractAddress, web3j, transactionManager, gasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> issueDegree(
            String globalCredentialId,
            String universityCode,
            String rollNo,
            String studentName,
            String cgpa,
            String dataHash
    ) {
        Function function = new Function(
                FUNC_ISSUEDEGREE,
                List.of(
                        new Utf8String(globalCredentialId),
                        new Utf8String(universityCode),
                        new Utf8String(rollNo),
                        new Utf8String(studentName),
                        new Utf8String(cgpa),
                        new Utf8String(dataHash)
                ),
                List.of()
        );
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> issueDegree(String rollNo, String studentId, String docHash, String ipfsCid) {
        String legacyId = "LEGACY-" + rollNo;
        return issueDegree(legacyId, "LEGACY", rollNo, studentId, "0", docHash);
    }

    public RemoteFunctionCall<TransactionReceipt> issueBatch(
            List<String> globalCredentialIds,
            List<String> universityCodes,
            List<String> rollNos,
            List<String> studentNames,
            List<String> cgpAs,
            List<String> dataHashes
    ) {
        Function function = new Function(
                FUNC_ISSUEBATCH,
                List.of(
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(globalCredentialIds)),
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(universityCodes)),
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(rollNos)),
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(studentNames)),
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(cgpAs)),
                        new DynamicArray<>(Utf8String.class, wrapUtf8Strings(dataHashes))
                ),
                List.of()
        );
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> issueBatch(
            List<String> rollNos,
            List<String> studentIds,
            List<String> docHashes,
            List<String> ipfsCids
    ) {
        List<String> globalIds = new ArrayList<>();
        List<String> universityCodes = new ArrayList<>();
        List<String> studentNames = new ArrayList<>();
        List<String> cgpAs = new ArrayList<>();
        for (int i = 0; i < rollNos.size(); i++) {
            globalIds.add("LEGACY-" + rollNos.get(i));
            universityCodes.add("LEGACY");
            studentNames.add(studentIds.get(i));
            cgpAs.add("0");
        }
        return issueBatch(globalIds, universityCodes, rollNos, studentNames, cgpAs, docHashes);
    }

    public RemoteFunctionCall<Tuple8<String, String, String, String, String, String, String, BigInteger>> verifyDegree(String globalCredentialId) {
        Function function = new Function(
                FUNC_VERIFYDEGREE,
                List.of(new Utf8String(globalCredentialId)),
                List.of(
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<Utf8String>() {},
                        new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new org.web3j.abi.TypeReference<Uint256>() {}
                )
        );
        return new RemoteFunctionCall<>(function, () -> {
            List<Type> results = executeCallMultipleValueReturn(function);
            return new Tuple8<>(
                    (String) results.get(0).getValue(),
                    (String) results.get(1).getValue(),
                    (String) results.get(2).getValue(),
                    (String) results.get(3).getValue(),
                    (String) results.get(4).getValue(),
                    (String) results.get(5).getValue(),
                    (String) results.get(6).getValue(),
                    (BigInteger) results.get(7).getValue()
            );
        });
    }

    public RemoteFunctionCall<BigInteger> getDegreeCount() {
        Function function = new Function(FUNC_GETDEGREECOUNT, List.of(), List.of(new org.web3j.abi.TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getBatchSize() {
        Function function = new Function(FUNC_GETBATCHSIZE, List.of(), List.of(new org.web3j.abi.TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> universityAdmin() {
        Function function = new Function(
                FUNC_UNIVERSITYADMIN,
                List.of(),
                List.of(new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Address>() {})
        );
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    private static List<Utf8String> wrapUtf8Strings(List<String> values) {
        return values.stream().map(Utf8String::new).collect(Collectors.toCollection(ArrayList::new));
    }
}

